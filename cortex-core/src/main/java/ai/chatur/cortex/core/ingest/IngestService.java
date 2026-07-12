package ai.chatur.cortex.core.ingest;

import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.core.CortexNames;
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
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.apache.jena.vocabulary.DCTerms;

public class IngestService {

  private final Dataset assertions;
  private final ShaclValidator shaclValidator;
  private final Shapes shapes;

  public IngestService(Dataset assertions, ShaclValidator shaclValidator, Shapes shapes) {
    this.assertions = assertions;
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

  public IngestResult ingest(String ttl) throws IOException {
    StringReader reader = new StringReader(ttl);
    Resource namedModel = CortexNames.getResource();
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, reader, null, Lang.TTL);
    ValidationReport validationReport = validate(model);
    if (validationReport.conforms()) {
      Txn.executeWrite(assertions, () -> assertions.addNamedModel(namedModel, model));
      return new IngestResult(true, namedModel.getLocalName(), null);
    }
    return new IngestResult(false, null, getErrors(validationReport));
  }

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

  public boolean hasBranch(String branch) {
    Resource namedModel = CortexNames.getResource(branch);
    return Txn.calculateRead(assertions, () -> assertions.containsNamedModel(namedModel));
  }

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

  public boolean approve(String branch) {
    if (!hasBranch(branch)) return false;
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

  public void reject(String branch) {
    if (hasBranch(branch)) {
      Txn.calculateWrite(
          assertions,
          () -> {
            Resource namedModel = CortexNames.getResource(branch);
            assertions.removeNamedModel(namedModel);
            return true;
          });
    }
  }

  public String getAssertions() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      Txn.executeRead(
          assertions, () -> RDFDataMgr.write(writer, assertions.getDefaultModel(), Lang.TRIG));
    }
    return writer.toString();
  }
}
