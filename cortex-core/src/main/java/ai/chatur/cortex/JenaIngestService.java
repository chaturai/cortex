package ai.chatur.cortex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.UUID;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
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

public class JenaIngestService implements IngestService {
  DatasetGraph assertions;
  ShaclValidator shaclValidator;
  Shapes shapes;

  public JenaIngestService(Dataset ds, ShaclValidator shaclValidator, Shapes shapes) {
    this.assertions = ds.asDatasetGraph();
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

  private ValidationReport validate(Graph graph) {
    return shaclValidator.validate(shapes, graph);
  }

  private String getErrors(ValidationReport validationReport) throws IOException {
    if (validationReport.conforms()) return null;
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      ShLib.printReport(os, validationReport);
      return os.toString();
    }
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
}
