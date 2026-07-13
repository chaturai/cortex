package ai.chatur.cortex.core.ingest;

import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.LintResult;
import ai.chatur.cortex.core.CortexNames;
import ai.chatur.cortex.core.lint.LintService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.apache.jena.vocabulary.DCTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the branch-based ingestion workflow of the knowledge graph.
 *
 * <p>Incoming assertions are linted against the ontology, validated against SHACL shapes, and
 * staged on a branch — a named graph within the assertions dataset. Approving a branch reifies each
 * staged statement with a {@code dcterms:created} timestamp and merges it into the default graph;
 * rejecting a branch discards it.
 */
public class IngestService {

  private static final Logger log = LoggerFactory.getLogger(IngestService.class);

  private final Dataset assertions;
  private final LintService lintService;
  private final ShaclValidator shaclValidator;
  private final Shapes shapes;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   * @param lintService the lint check incoming assertions must pass
   * @param shaclValidator the validator applied to incoming assertions
   * @param shapes the SHACL shapes incoming assertions must conform to
   */
  public IngestService(
      Dataset assertions, LintService lintService, ShaclValidator shaclValidator, Shapes shapes) {
    this.assertions = assertions;
    this.lintService = lintService;
    this.shaclValidator = shaclValidator;
    this.shapes = shapes;
  }

  ValidationReport validate(Model model) {
    return shaclValidator.validate(shapes, model.getGraph());
  }

  String getErrors(ValidationReport validationReport) throws IOException {
    if (validationReport.conforms()) return null;
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      ShLib.printReport(os, validationReport);
      return os.toString();
    }
  }

  /**
   * Lints and validates the given assertions and stages them on a new branch.
   *
   * <p>Assertions that cannot be parsed, fail the {@link LintService lint check} against the
   * ontology, or do not conform to the shapes are not staged; the problem is reported in the result
   * instead.
   *
   * @param ttl RDF assertions in Turtle syntax
   * @return the outcome, carrying either the name of the created branch or the errors
   * @throws IOException if the validation report cannot be rendered
   */
  public IngestResult ingest(String ttl) throws IOException {
    LintResult lintResult = lintService.lint(ttl);
    if (!lintResult.valid()) {
      log.warn("Rejected ingest failing lint check: {}", lintResult.errors());
      return new IngestResult(false, null, lintResult.errors());
    }
    Resource namedModel = CortexNames.getResource();
    Model model = ModelFactory.createDefaultModel();
    try {
      RDFDataMgr.read(model, new StringReader(ttl), null, Lang.TTL);
    } catch (RiotException e) {
      log.warn("Rejected ingest of malformed Turtle: {}", e.getMessage());
      return new IngestResult(false, null, e.getMessage());
    }
    ValidationReport validationReport = validate(model);
    if (validationReport.conforms()) {
      Txn.executeWrite(assertions, () -> assertions.addNamedModel(namedModel, model));
      log.info("Staged {} triples on branch {}", model.size(), namedModel.getLocalName());
      return new IngestResult(true, namedModel.getLocalName(), null);
    }
    String errors = getErrors(validationReport);
    log.warn("Rejected ingest of {} triples failing SHACL validation: {}", model.size(), errors);
    return new IngestResult(false, null, errors);
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
    Resource namedModel = CortexNames.getResource(branch);
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
    Resource namedModel = CortexNames.getResource(branch);
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
   * Merges the assertions staged on the given branch into the default graph, recording a creation
   * timestamp against each statement, and deletes the branch.
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
    Resource namedModel = CortexNames.getResource(branch);
    collector.txnBegin();
    Txn.executeRead(
        assertions,
        () ->
            getProvenanced(assertions.getNamedModel(namedModel)).getGraph().stream()
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

  Model getProvenanced(Model model) {
    Model provModel = ModelFactory.createDefaultModel();
    Literal now = provModel.createTypedLiteral(Calendar.getInstance());
    model
        .listStatements()
        .forEach(
            statement -> {
              provModel.add(statement);
              Resource quoted = provModel.createReifier(statement);
              provModel.add(quoted, DCTerms.created, now);
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
          Resource namedModel = CortexNames.getResource(branch);
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
