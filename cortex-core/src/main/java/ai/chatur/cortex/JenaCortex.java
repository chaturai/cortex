package ai.chatur.cortex;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
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

public class JenaCortex implements Cortex {
  Dataset assertions;
  OntModel ontModel;
  ShaclValidator shaclValidator;
  Shapes shapes;

  public JenaCortex(
      Dataset assertions, OntModel ontModel, ShaclValidator shaclValidator, Shapes shapes) {
    Model model = ModelFactory.createDefaultModel();
    model.setNsPrefixes(ontModel.getNsPrefixMap());
    Txn.executeWrite(assertions, () -> assertions.setDefaultModel(model));
    this.assertions = assertions;
    this.ontModel = ontModel;
    this.shaclValidator = shaclValidator;
    this.shapes = shapes;
  }

  public static String NS = "cortex://";

  Resource getResource(String branch) {
    return ResourceFactory.createResource(NS + branch);
  }

  Resource getResource() {
    UUID uuid = UUID.randomUUID();
    return getResource("branch-" + uuid.toString());
  }

  Node getNode(String id) {
    return NodeFactory.createURI(NS + id);
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

  @Override
  public String getOntology() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      RDFDataMgr.write(writer, ontModel, Lang.TTL);
    }
    return writer.toString();
  }

  @Override
  public IngestResult ingest(String ttl) throws IOException {
    StringReader reader = new StringReader(ttl);
    Resource namedModel = getResource();
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, reader, null, Lang.TTL);
    ValidationReport validationReport = validate(model);
    if (validationReport.conforms()) {
      Txn.executeWrite(assertions, () -> assertions.addNamedModel(namedModel, model));
      return new IngestResult(true, namedModel.getLocalName(), null);
    }
    return new IngestResult(false, null, getErrors(validationReport));
  }

  @Override
  public List<String> listBranches() {
    List<String> branches = new ArrayList<>();
    Txn.executeRead(
        assertions,
        () ->
            assertions
                .listModelNames()
                .forEachRemaining(
                    (node) -> {
                      System.out.println(node.getURI());
                      branches.add(node.getLocalName());
                    }));
    return branches;
  }

  @Override
  public boolean hasBranch(String branch) {
    Resource namedModel = getResource(branch);
    return Txn.calculateRead(assertions, () -> assertions.containsNamedModel(namedModel));
  }

  @Override
  public String getBranch(String branch) throws IOException {
    Resource namedModel = getResource(branch);
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

  @Override
  public void approve(String branch) {
    if (hasBranch(branch)) {
      RDFChangesCollector collector = new RDFChangesCollector();
      Resource namedModel = getResource(branch);
      collector.txnBegin();
      Txn.executeRead(
          assertions,
          () ->
              assertions.getNamedModel(namedModel).getGraph().stream()
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
    }
  }

  @Override
  public void reject(String branch) {
    if (hasBranch(branch)) {
      Txn.calculateWrite(
          assertions,
          () -> {
            Resource namedModel = getResource(branch);
            assertions.removeNamedModel(namedModel);
            return true;
          });
    }
  }

  @Override
  public String getAssertions() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      Txn.executeRead(
          assertions, () -> RDFDataMgr.write(writer, assertions.getDefaultModel(), Lang.TRIG));
    }
    return writer.toString();
  }

  QueryExecution getQueryExecution(Query query) {
    return QueryExecution.dataset(assertions).query(query).build();
  }

  @Override
  public String describe(String id) throws IOException {
    Node node = getNode(id);
    Query query = QueryFactory.create();
    query.setQueryDescribeType();
    query.addDescribeNode(node);
    QueryExecution queryExecution = getQueryExecution(query);
    Model description = Txn.calculateRead(assertions, queryExecution::execDescribe);
    StringWriter writer = new StringWriter();
    try (writer) {
      description.write(writer, "TTL");
    }
    return writer.toString();
  }
}
