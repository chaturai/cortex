package ai.chatur.cortex.core.ingest;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.LintResult;
import ai.chatur.cortex.ResourceEdit;
import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.lint.LintService;
import ai.chatur.cortex.core.provenance.ProvenanceRecorder;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.jena.graph.compose.Difference;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates incoming RDF assertions, and proposed edits to approved ones, and stages them on a new
 * branch pending review.
 *
 * <p>Incoming assertions are linted against the ontology, validated against SHACL shapes together
 * with the approved assertions, trimmed of triples already approved, and staged on a branch — a
 * named graph within the assertions dataset. Every branch carries a {@link
 * ai.chatur.cortex.core.PROV#Activity provenance activity} recording the ingestion, built by the
 * {@link ProvenanceRecorder}. Reviewing, editing, and resolving a branch once it is staged are the
 * jobs of {@code core.branch}'s services, not this one.
 *
 * <p>{@link #revise} stages the other kind of proposal: a change to statements that are
 * <em>already</em> approved. It is the same pipeline and produces the same kind of branch, so the
 * rule that nothing reaches the approved assertions without review holds for edits exactly as it
 * does for new claims. The one structural difference is that an edit has to retract as well as
 * assert, which the branch carries in its {@link CortexNamespace#getRetractions retraction graph}.
 */
public class IngestService {

  private static final Logger log = LoggerFactory.getLogger(IngestService.class);

  private final Dataset assertions;
  private final LintService lintService;
  private final ProvenanceRecorder provenanceRecorder;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   * @param lintService the lint check and SHACL validation incoming assertions must pass
   * @param provenanceRecorder builds the provenance activity recorded when a branch is staged
   */
  public IngestService(
      Dataset assertions, LintService lintService, ProvenanceRecorder provenanceRecorder) {
    this.assertions = assertions;
    this.lintService = lintService;
    this.provenanceRecorder = provenanceRecorder;
  }

  /**
   * Lints and validates the given assertions and stages them on a new branch.
   *
   * <p>Assertions that cannot be parsed, fail the {@link LintService lint check} against the
   * ontology, or do not conform to the shapes are not staged; the problem is reported in the result
   * instead. The shapes are validated against the union of the approved assertions and the incoming
   * ones, so incoming assertions may rely on already approved statements to conform.
   *
   * <p>Incoming triples already present among the approved assertions are trimmed before staging,
   * so a branch only ever carries novel statements. If every triple is already approved, nothing is
   * staged and the result carries no branch. The staged branch also carries a {@link
   * ai.chatur.cortex.core.PROV#Activity provenance activity} recording when the ingestion was
   * staged.
   *
   * <p>The SHACL validation, the novelty diff, and the staging write all happen inside one write
   * transaction on the assertions dataset, so a concurrent {@code approve} landing between the
   * validation and the write can no longer make either stale: TDB2 serializes writers, so this
   * ingestion either sees the approval's result in full (and validates and diffs against it) or
   * runs entirely before it. Parsing the incoming Turtle happens first, outside the transaction,
   * since it depends on nothing from the dataset.
   *
   * @param ttl RDF assertions in Turtle syntax
   * @return the outcome, carrying either the name of the created branch — {@code null} if every
   *     triple was already approved — or the errors
   */
  public IngestResult ingest(String ttl) {
    LintResult lintResult = lintService.lint(ttl);
    if (!lintResult.valid()) {
      log.warn("Rejected ingest failing lint check: {}", lintResult.errors());
      return new IngestResult(false, null, lintResult.errors());
    }
    Model model = ModelFactory.createDefaultModel();
    try {
      RDFDataMgr.read(model, new StringReader(ttl), null, Lang.TTL);
    } catch (RiotException e) {
      log.warn("Rejected ingest of malformed Turtle: {}", e.getMessage());
      return new IngestResult(false, null, e.getMessage());
    }
    return Txn.calculateWrite(
        assertions,
        () -> {
          ValidationReport validationReport =
              lintService.validate(ModelFactory.createUnion(assertions.getDefaultModel(), model));
          if (!validationReport.conforms()) {
            String errors = lintService.getErrors(validationReport);
            log.warn(
                "Rejected ingest of {} triples failing SHACL validation: {}", model.size(), errors);
            return new IngestResult(false, null, errors);
          }
          Model novel = model.difference(assertions.getDefaultModel());
          if (novel.isEmpty()) {
            log.info("Nothing to stage: all {} triples are already approved", model.size());
            return new IngestResult(true, null, null);
          }
          Resource namedModel = CortexNamespace.getResource();
          Model staged = provenanceRecorder.getStagedModel(novel, namedModel);
          assertions.getNamedModel(namedModel).add(staged);
          log.info(
              "Staged {} of {} triples on branch {}",
              novel.size(),
              model.size(),
              namedModel.getLocalName());
          return new IngestResult(true, namedModel.getLocalName(), null);
        });
  }

  /**
   * Stages a proposed edit to an already-approved resource on a new branch.
   *
   * <p>Changing the value of an approved statement is a retraction and an assertion, not just an
   * assertion, so the branch this stages carries both: the novel statements in the branch graph and
   * the statements to remove in its {@link CortexNamespace#getRetractions retraction graph}.
   * Neither takes effect until the branch is approved.
   *
   * <p>The additions are linted against the ontology, and the SHACL shapes are validated against
   * the assertions <em>as they would be once the branch is approved</em> — the approved statements
   * minus the retractions, plus the additions. That is stricter than {@link #ingest}, which
   * validates a union and so can only ever see constraints satisfied: a retraction can take away
   * the statement that was satisfying a constraint, and validating the union would not notice.
   *
   * <p>A rename is applied after the changes, since the changes address the resource by its current
   * IRI. It rewrites every statement in which the IRI appears — as subject or as object — so no
   * reference is left pointing at a resource that no longer exists.
   *
   * <p>Additions already approved and retractions of statements that are not approved are dropped,
   * as is any statement the same edit both retracts and asserts. If nothing remains, no branch is
   * created and the result carries none, exactly as an ingest of entirely known statements does.
   *
   * @param edit the proposed edit
   * @return the outcome, carrying either the name of the created branch — {@code null} if the edit
   *     would change nothing — or the lint or validation errors
   */
  public IngestResult revise(ResourceEdit edit) {
    return Txn.calculateWrite(
        assertions,
        () -> {
          Model approved = assertions.getDefaultModel();
          Model additions = ModelFactory.createDefaultModel().setNsPrefixes(approved);
          Model retractions = ModelFactory.createDefaultModel().setNsPrefixes(approved);
          for (BranchChange change : edit.changes()) {
            Resource subject = approved.createResource(expand(approved, change.subject()));
            Property predicate = approved.createProperty(expand(approved, change.predicate()));
            if (change.object() != null) {
              retractions.add(subject, predicate, toObject(approved, change, change.object()));
            }
            if (change.newObject() != null) {
              additions.add(subject, predicate, toObject(approved, change, change.newObject()));
            }
          }
          if (edit.subject() != null && edit.newSubject() != null) {
            rename(
                approved,
                additions,
                retractions,
                approved.createResource(expand(approved, edit.subject())),
                approved.createResource(expand(approved, edit.newSubject())));
          }
          return stage(approved, additions, retractions);
        });
  }

  /**
   * Validates the proposed additions and retractions and stages them on a new branch.
   *
   * @param approved the approved assertions
   * @param additions the statements the edit asserts
   * @param retractions the statements the edit retracts
   * @return the outcome, carrying the branch name, no branch, or the errors
   */
  IngestResult stage(Model approved, Model additions, Model retractions) {
    Set<String> violations = lintService.getViolations(additions);
    if (!violations.isEmpty()) {
      String errors = String.join("\n", violations);
      log.warn("Rejected revision failing lint check: {}", errors);
      return new IngestResult(false, null, errors);
    }
    ValidationReport validationReport =
        lintService.validate(prospective(approved, additions, retractions));
    if (!validationReport.conforms()) {
      String errors = lintService.getErrors(validationReport);
      log.warn("Rejected revision failing SHACL validation: {}", errors);
      return new IngestResult(false, null, errors);
    }
    Model novel = additions.difference(approved);
    Model removed = retractions.intersection(approved).difference(additions);
    if (novel.isEmpty() && removed.isEmpty()) {
      log.info("Nothing to stage: the revision changes nothing");
      return new IngestResult(true, null, null);
    }
    Resource namedModel = CortexNamespace.getResource();
    assertions.getNamedModel(namedModel).add(provenanceRecorder.getStagedModel(novel, namedModel));
    if (!removed.isEmpty()) {
      assertions.getNamedModel(CortexNamespace.getRetractions(namedModel)).add(removed);
    }
    log.info(
        "Staged a revision of {} additions and {} retractions on branch {}",
        novel.size(),
        removed.size(),
        namedModel.getLocalName());
    return new IngestResult(true, namedModel.getLocalName(), null);
  }

  /**
   * Extends the edit so that a renamed resource keeps everything said about it and everything said
   * with it, under its new IRI.
   *
   * <p>Every statement in which the old IRI appears is retracted where it is approved, and added
   * back rewritten. It reads the assertions as the edit would leave them, not as they stand, so a
   * statement the same edit is already changing is renamed in its edited form rather than
   * resurrected in its original one.
   *
   * @param approved the approved assertions
   * @param additions the statements the edit asserts, extended in place
   * @param retractions the statements the edit retracts, extended in place
   * @param from the IRI being renamed
   * @param to the IRI to rename it to
   */
  void rename(Model approved, Model additions, Model retractions, Resource from, Resource to) {
    Model prospective = prospective(approved, additions, retractions);
    List<Statement> touched = new ArrayList<>();
    prospective.listStatements(from, null, (RDFNode) null).forEach(touched::add);
    prospective.listStatements(null, null, from).forEach(touched::add);
    for (Statement statement : touched) {
      if (approved.contains(statement)) retractions.add(statement);
      additions.remove(statement);
      additions.add(
          from.equals(statement.getSubject()) ? to : statement.getSubject(),
          statement.getPredicate(),
          from.equals(statement.getObject()) ? to : statement.getObject());
    }
  }

  /**
   * Returns the assertions as they would stand once the edit is approved.
   *
   * <p>A view over the three models rather than a copy: the approved assertions can be large, and
   * this is read once per revision to validate the shapes.
   *
   * @param approved the approved assertions
   * @param additions the statements the edit asserts
   * @param retractions the statements the edit retracts
   * @return the approved assertions less the retractions, plus the additions
   */
  Model prospective(Model approved, Model additions, Model retractions) {
    return ModelFactory.createUnion(
        ModelFactory.createModelForGraph(
            new Difference(approved.getGraph(), retractions.getGraph())),
        additions);
  }

  /**
   * Builds the node an edited or newly added value denotes.
   *
   * <p>An edit keeps the kind of the value it replaces — the page that proposed it echoes back
   * whether the object was a resource, a typed literal, or a language-tagged one. A value being
   * added has no predecessor to take that from, so it is {@link #isResource inferred}.
   *
   * @param prefixes the prefix mapping to resolve a prefixed IRI against
   * @param change the change the value belongs to
   * @param value the IRI or lexical form
   * @return the node the value denotes
   */
  RDFNode toObject(Model prefixes, BranchChange change, String value) {
    String expanded = expand(prefixes, value);
    if (change.object() == null) {
      return isResource(value, expanded)
          ? prefixes.createResource(expanded)
          : prefixes.createLiteral(value);
    }
    if (!change.literal()) return prefixes.createResource(expanded);
    if (change.language() != null) return prefixes.createLiteral(value, change.language());
    if (change.datatype() == null) return prefixes.createLiteral(value);
    return prefixes.createTypedLiteral(value, change.datatype());
  }

  /**
   * Reports whether a value with no predecessor to take its kind from denotes a resource rather
   * than a literal.
   *
   * <p>Only two things count: a declared prefix that actually expanded, or a scheme written out in
   * full. Anything else is a literal — guessing more liberally would turn ordinary prose such as
   * {@code "note: call back"} into an unresolvable IRI, and there is nothing downstream that would
   * catch it, since an object IRI is not checked against the ontology the way a predicate is.
   *
   * @param value the value as written
   * @param expanded the value with any declared prefix expanded
   * @return {@code true} if the value denotes a resource
   */
  boolean isResource(String value, String expanded) {
    return !expanded.equals(value) || value.contains("://");
  }

  /**
   * Expands a prefixed name against the prefix mapping, leaving anything else alone.
   *
   * @param prefixes the prefix mapping
   * @param name the possibly prefixed name
   * @return the expanded name, or {@code name} unchanged if no declared prefix matched
   */
  String expand(Model prefixes, String name) {
    return prefixes.expandPrefix(name);
  }
}
