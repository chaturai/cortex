package ai.chatur.cortex.core.ingest;

import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.LintResult;
import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.lint.LintService;
import ai.chatur.cortex.core.provenance.ProvenanceRecorder;
import java.io.StringReader;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates incoming RDF assertions and stages them on a new branch pending review.
 *
 * <p>Incoming assertions are linted against the ontology, validated against SHACL shapes together
 * with the approved assertions, trimmed of triples already approved, and staged on a branch — a
 * named graph within the assertions dataset. Every branch carries a {@link
 * ai.chatur.cortex.core.PROV#Activity provenance activity} recording the ingestion, built by the
 * {@link ProvenanceRecorder}. Reviewing, editing, and resolving a branch once it is staged are the
 * jobs of {@code core.branch}'s services, not this one.
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
}
