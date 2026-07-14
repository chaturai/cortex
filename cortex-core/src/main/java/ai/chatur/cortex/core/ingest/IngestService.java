package ai.chatur.cortex.core.ingest;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchInfo;
import ai.chatur.cortex.BranchRename;
import ai.chatur.cortex.BranchStatement;
import ai.chatur.cortex.BranchSubject;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.LintResult;
import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.PROV;
import ai.chatur.cortex.core.lint.LintService;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the branch-based ingestion workflow of the knowledge graph.
 *
 * <p>Incoming assertions are linted against the ontology, validated against SHACL shapes together
 * with the approved assertions, trimmed of triples already approved, and staged on a branch — a
 * named graph within the assertions dataset. Every branch carries a {@link PROV#Activity provenance
 * activity} recording the ingestion. Reviewers may delete or edit staged statements. Approving a
 * branch reifies each staged statement, links it to the activity, and merges everything into the
 * default graph; rejecting a branch discards it.
 */
public class IngestService {

  private static final Logger log = LoggerFactory.getLogger(IngestService.class);

  private final Dataset assertions;
  private final LintService lintService;
  private final OntModel ontModel;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   * @param lintService the lint check and SHACL validation incoming assertions must pass
   * @param ontModel the ontology model, used to abbreviate terms for display
   */
  public IngestService(Dataset assertions, LintService lintService, OntModel ontModel) {
    this.assertions = assertions;
    this.lintService = lintService;
    this.ontModel = ontModel;
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
   * staged and the result carries no branch. The staged branch also carries a {@link PROV#Activity
   * provenance activity} recording when the ingestion was staged.
   *
   * @param ttl RDF assertions in Turtle syntax
   * @return the outcome, carrying either the name of the created branch — {@code null} if every
   *     triple was already approved — or the errors
   * @throws IOException if the validation report cannot be rendered
   */
  public IngestResult ingest(String ttl) throws IOException {
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
    ValidationReport validationReport =
        Txn.calculateRead(
            assertions,
            () ->
                lintService.validate(
                    ModelFactory.createUnion(assertions.getDefaultModel(), model)));
    if (!validationReport.conforms()) {
      String errors = lintService.getErrors(validationReport);
      log.warn("Rejected ingest of {} triples failing SHACL validation: {}", model.size(), errors);
      return new IngestResult(false, null, errors);
    }
    Model novel =
        Txn.calculateRead(assertions, () -> model.difference(assertions.getDefaultModel()));
    if (novel.isEmpty()) {
      log.info("Nothing to stage: all {} triples are already approved", model.size());
      return new IngestResult(true, null, null);
    }
    Resource namedModel = CortexNamespace.getResource();
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.txnBegin();
    getActivity(novel, namedModel).getGraph().stream()
        .forEach(
            triple ->
                collector.add(
                    namedModel.asNode(),
                    triple.getSubject(),
                    triple.getPredicate(),
                    triple.getObject()));
    collector.txnCommit();
    RDFPatchOps.applyChange(assertions.asDatasetGraph(), collector.getRDFPatch());
    log.info(
        "Staged {} of {} triples on branch {}",
        novel.size(),
        model.size(),
        namedModel.getLocalName());
    return new IngestResult(true, namedModel.getLocalName(), null);
  }

  Model getActivity(Model novel, Resource activity) {
    Model staged = ModelFactory.createDefaultModel();
    staged.add(novel);
    Literal now = staged.createTypedLiteral(Calendar.getInstance());
    staged.add(activity, RDF.type, PROV.Activity);
    staged.add(activity, RDFS.label, activity.getLocalName());
    staged.add(activity, RDFS.comment, "Ingestion of the assertions staged on this branch");
    staged.add(activity, PROV.startedAtTime, now);
    return staged;
  }

  /**
   * Returns the names of all branches pending review.
   *
   * @return the branch names, empty if nothing is pending
   */
  public List<String> listBranches() {
    List<String> branches = new ArrayList<>();
    Txn.executeRead(
        assertions,
        () ->
            assertions
                .listModelNames()
                .forEachRemaining(
                    (node) -> {
                      branches.add(node.getLocalName());
                    }));
    return branches;
  }

  /**
   * Reports whether a branch with the given name is pending review.
   *
   * @param branch the branch name
   * @return {@code true} if the branch exists
   */
  public boolean hasBranch(String branch) {
    Resource namedModel = CortexNamespace.getResource(branch);
    return Txn.calculateRead(assertions, () -> assertions.containsNamedModel(namedModel));
  }

  /**
   * Returns the assertions staged on the given branch.
   *
   * @param branch the branch name
   * @return the staged assertions serialized in Turtle syntax
   * @throws IOException if the assertions cannot be serialized
   */
  public String getBranch(String branch) throws IOException {
    Resource namedModel = CortexNamespace.getResource(branch);
    StringWriter writer = new StringWriter();
    try (writer) {
      Txn.executeRead(
          assertions,
          () -> {
            Model model = assertions.getNamedModel(namedModel);
            model.write(writer, "TTL");
          });
      return writer.toString();
    }
  }

  /**
   * Summarizes a branch pending review from its staged provenance activity.
   *
   * @param branch the branch name
   * @return the branch summary
   */
  public BranchInfo getBranchInfo(String branch) {
    Resource namedModel = CortexNamespace.getResource(branch);
    return Txn.calculateRead(
        assertions,
        () -> {
          Model model = assertions.getNamedModel(namedModel);
          Statement started = model.getProperty(namedModel, PROV.startedAtTime);
          long activitySize = Iter.count(model.listStatements(namedModel, null, (RDFNode) null));
          return new BranchInfo(
              branch,
              started == null ? null : started.getLiteral().getLexicalForm(),
              model.size() - activitySize);
        });
  }

  /**
   * Returns the assertions staged on the given branch grouped by subject, excluding the provenance
   * activity of the ingestion.
   *
   * @param branch the branch name
   * @return the staged subjects sorted by name, each with its statements sorted by predicate
   */
  public List<BranchSubject> getBranchSubjects(String branch) {
    Resource namedModel = CortexNamespace.getResource(branch);
    return Txn.calculateRead(
        assertions,
        () -> {
          Map<Resource, List<BranchStatement>> subjects =
              new TreeMap<>(Comparator.comparing(Resource::toString));
          assertions
              .getNamedModel(namedModel)
              .listStatements()
              .forEach(
                  statement -> {
                    Resource subject = statement.getSubject();
                    if (namedModel.equals(subject)) return;
                    subjects
                        .computeIfAbsent(subject, key -> new ArrayList<>())
                        .add(getBranchStatement(statement));
                  });
          return subjects.entrySet().stream()
              .map(
                  entry ->
                      new BranchSubject(
                          entry.getKey().isURIResource()
                              ? entry.getKey().getLocalName()
                              : entry.getKey().toString(),
                          entry.getKey().toString(),
                          entry.getValue().stream()
                              .sorted(
                                  Comparator.comparing(BranchStatement::predicate)
                                      .thenComparing(BranchStatement::object))
                              .toList()))
              .toList();
        });
  }

  BranchStatement getBranchStatement(Statement statement) {
    RDFNode object = statement.getObject();
    boolean literal = object.isLiteral();
    return new BranchStatement(
        ontModel.shortForm(statement.getPredicate().getURI()),
        statement.getPredicate().getURI(),
        literal ? object.asLiteral().getLexicalForm() : object.toString(),
        literal,
        literal ? object.asLiteral().getDatatypeURI() : null);
  }

  /**
   * Applies reviewer changes — deletions and object edits — to the assertions staged on the given
   * branch, as an RDF patch on the branch graph.
   *
   * <p>Changes addressing the provenance activity of the branch are ignored.
   *
   * @param branch the branch name
   * @param changes the changes to apply
   * @return {@code true} if the branch existed and the changes were applied
   */
  public boolean updateBranch(String branch, List<BranchChange> changes) {
    if (!hasBranch(branch)) {
      log.warn("Cannot update unknown branch {}", branch);
      return false;
    }
    Resource namedModel = CortexNamespace.getResource(branch);
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.txnBegin();
    for (BranchChange change : changes) {
      if (namedModel.getURI().equals(change.subject())) {
        log.warn("Ignoring change to the provenance activity of branch {}", branch);
        continue;
      }
      Node subject = NodeFactory.createURI(change.subject());
      Node predicate = NodeFactory.createURI(change.predicate());
      collector.delete(namedModel.asNode(), subject, predicate, getObject(change.object(), change));
      if (change.newObject() != null) {
        collector.add(
            namedModel.asNode(), subject, predicate, getObject(change.newObject(), change));
      }
    }
    collector.txnCommit();
    RDFPatchOps.applyChange(assertions.asDatasetGraph(), collector.getRDFPatch());
    log.info("Updated branch {} with {} changes", branch, changes.size());
    return true;
  }

  /**
   * Renames subjects staged on the given branch, rewriting every staged statement in which a
   * renamed subject appears — as subject or object — to use its new IRI, as an RDF patch on the
   * branch graph.
   *
   * <p>Renames addressing the provenance activity of the branch are ignored.
   *
   * @param branch the branch name
   * @param renames the renames to apply
   * @return {@code true} if the branch existed and the renames were applied
   */
  public boolean renameBranchSubjects(String branch, List<BranchRename> renames) {
    if (!hasBranch(branch)) {
      log.warn("Cannot rename subjects on unknown branch {}", branch);
      return false;
    }
    Resource namedModel = CortexNamespace.getResource(branch);
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.txnBegin();
    for (BranchRename rename : renames) {
      if (namedModel.getURI().equals(rename.subject())) {
        log.warn("Ignoring rename of the provenance activity of branch {}", branch);
        continue;
      }
      Node subject = NodeFactory.createURI(rename.subject());
      Node newSubject = NodeFactory.createURI(rename.newSubject());
      Txn.executeRead(
          assertions,
          () ->
              assertions.getNamedModel(namedModel).getGraph().stream()
                  .filter(
                      triple ->
                          triple.getSubject().equals(subject) || triple.getObject().equals(subject))
                  .forEach(
                      triple -> {
                        collector.delete(
                            namedModel.asNode(),
                            triple.getSubject(),
                            triple.getPredicate(),
                            triple.getObject());
                        collector.add(
                            namedModel.asNode(),
                            triple.getSubject().equals(subject) ? newSubject : triple.getSubject(),
                            triple.getPredicate(),
                            triple.getObject().equals(subject) ? newSubject : triple.getObject());
                      }));
    }
    collector.txnCommit();
    RDFPatchOps.applyChange(assertions.asDatasetGraph(), collector.getRDFPatch());
    log.info("Renamed {} subjects on branch {}", renames.size(), branch);
    return true;
  }

  Node getObject(String value, BranchChange change) {
    if (!change.literal()) return NodeFactory.createURI(value);
    if (change.datatype() == null) return NodeFactory.createLiteralString(value);
    return NodeFactory.createLiteralDT(value, NodeFactory.getType(change.datatype()));
  }

  /**
   * Merges the assertions staged on the given branch into the default graph, closing the {@link
   * PROV#Activity provenance activity} of the ingestion and linking every merged statement to it,
   * and deletes the branch.
   *
   * <p>Staged triples that were approved through another branch in the meantime are skipped, so a
   * statement is never merged or reified twice.
   *
   * @param branch the branch name
   * @return {@code true} if the branch existed and was merged
   */
  public boolean approve(String branch) {
    if (!hasBranch(branch)) {
      log.warn("Cannot approve unknown branch {}", branch);
      return false;
    }
    RDFChangesCollector collector = new RDFChangesCollector();
    Resource namedModel = CortexNamespace.getResource(branch);
    collector.txnBegin();
    Txn.executeRead(
        assertions,
        () ->
            getProvenanced(
                    assertions.getNamedModel(namedModel).difference(assertions.getDefaultModel()),
                    namedModel)
                .getGraph()
                .stream()
                .forEach(
                    triple -> {
                      collector.add(
                          Quad.defaultGraphIRI,
                          triple.getSubject(),
                          triple.getPredicate(),
                          triple.getObject());
                    }));
    collector.txnCommit();
    RDFPatch patch = collector.getRDFPatch();
    RDFPatchOps.applyChange(assertions.asDatasetGraph(), patch);
    Txn.executeWrite(assertions, () -> assertions.removeNamedModel(namedModel));
    log.info("Approved branch {}", branch);
    return true;
  }

  Model getProvenanced(Model model, Resource activity) {
    Model provModel = ModelFactory.createDefaultModel();
    Literal now = provModel.createTypedLiteral(Calendar.getInstance());
    provModel.add(activity, PROV.endedAtTime, now);
    model
        .listStatements()
        .forEach(
            statement -> {
              provModel.add(statement);
              if (!activity.equals(statement.getSubject())) {
                Resource quoted = provModel.createReifier(statement);
                provModel.add(quoted, PROV.wasGeneratedBy, activity);
              }
            });
    return provModel;
  }

  /**
   * Discards the assertions staged on the given branch, if it exists.
   *
   * @param branch the branch name
   */
  public void reject(String branch) {
    if (!hasBranch(branch)) {
      log.warn("Cannot reject unknown branch {}", branch);
      return;
    }
    Txn.calculateWrite(
        assertions,
        () -> {
          Resource namedModel = CortexNamespace.getResource(branch);
          assertions.removeNamedModel(namedModel);
          return true;
        });
    log.info("Rejected branch {}", branch);
  }

  /**
   * Returns all approved assertions.
   *
   * @return the default graph serialized in TriG syntax
   * @throws IOException if the assertions cannot be serialized
   */
  public String getAssertions() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      Txn.executeRead(
          assertions, () -> RDFDataMgr.write(writer, assertions.getDefaultModel(), Lang.TRIG));
    }
    return writer.toString();
  }
}
