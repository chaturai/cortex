package ai.chatur.cortex;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.system.Txn;

public class JenaCortex implements Cortex {
  DatasetGraph assertions;
  OntModel ontModel;
  ShaclValidator shaclValidator;
  Shapes shapes;

  public JenaCortex(Dataset ds, OntModel ontModel, ShaclValidator shaclValidator, Shapes shapes) {
    this.assertions = ds.asDatasetGraph();
    this.ontModel = ontModel;
    this.shaclValidator = shaclValidator;
    this.shapes = shapes;
  }

  public static String NS = "cortex://branches/";

  Node getGraphNode(String branch) {
    return NodeFactory.createURI(NS + branch);
  }

  Node getGraphNode() {
    UUID uuid = UUID.randomUUID();
    return getGraphNode(uuid.toString());
  }

  ValidationReport validate(Graph graph) {
    return shaclValidator.validate(shapes, graph);
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
    Node graphNode = getGraphNode();
    Graph graph = GraphFactory.createDefaultGraph();
    RDFDataMgr.read(graph, reader, null, Lang.TTL);
    ValidationReport validationReport = validate(graph);
    if (validationReport.conforms()) {
      Txn.executeWrite(assertions, () -> assertions.addGraph(graphNode, graph));
      return new IngestResult(true, graphNode.getURI(), null);
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
                .listGraphNodes()
                .forEachRemaining(
                    (node) -> {
                      branches.add(node.getURI());
                    }));
    return branches;
  }

  @Override
  public boolean hasBranch(String uri) {
    Node graphNode = NodeFactory.createURI(uri);
    return Txn.calculateRead(assertions, () -> assertions.containsGraph(graphNode));
  }

  @Override
  public boolean approve(String branch) {
    return false;
  }

  @Override
  public boolean reject(String branch) {
    return Txn.calculateWrite(
        assertions,
        () -> {
          Node graphNode = NodeFactory.createURI(branch);
          if (assertions.containsGraph(graphNode)) {
            assertions.removeGraph(graphNode);
            return true;
          }
          return false;
        });
  }

  @Override
  public void writeAssertions(OutputStream os) {
    Txn.executeRead(assertions, () -> RDFDataMgr.write(os, assertions, Lang.TRIG));
  }
}
