package ai.chatur.cortex;

import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
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

public class JenaCortex implements Cortex {
  Dataset assertions;
  OntModel ontModel;
  ShaclValidator shaclValidator;
  Shapes shapes;

  public JenaCortex(
      Dataset assertions, OntModel ontModel, ShaclValidator shaclValidator, Shapes shapes) {
    Txn.executeWrite(
        assertions, () -> assertions.getDefaultModel().setNsPrefixes(ontModel.getNsPrefixMap()));
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
    return getResource("branch-" + uuid);
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
      Txn.executeWrite(assertions, () -> assertions.addNamedModel(namedModel, provModel));
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
      Txn.executeWrite(assertions, () -> assertions.removeNamedModel(namedModel));
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
    return Txn.calculateRead(
        assertions,
        () -> {
          QueryExecution queryExecution = getQueryExecution(query);
          try (queryExecution) {
            Model description = queryExecution.execDescribe();
            StringWriter writer = new StringWriter();
            try (writer) {
              description.write(writer, "TTL");
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            return writer.toString();
          }
        });
  }

  public String query(String sparql) {
    Query query = QueryFactory.create(sparql);
    return Txn.calculateRead(
        assertions,
        () -> {
          QueryExecution queryExecution = getQueryExecution(query);
          try (queryExecution) {
            if (query.isSelectType()) {
              ResultSet resultSet = queryExecution.execSelect();
              return ResultSetFormatter.asText(resultSet);
            }
            if (query.isAskType()) {
              return String.valueOf(queryExecution.execAsk());
            }
            return null;
          }
        });
  }
}
