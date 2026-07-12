package ai.chatur.cortex;

import java.io.StringReader;
import java.util.UUID;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.system.Txn;

public class JenaIngestService implements IngestService {
  DatasetGraph assertions;
  String base;

  public static String NS = "cortex://assertions/";

  Node getGraphNode(String branch) {
    return NodeFactory.createURI(NS + branch);
  }

  Node getGraphNode() {
    UUID uuid = UUID.randomUUID();
    return getGraphNode(uuid.toString());
  }

  public JenaIngestService(DatasetGraph assertions) {
    this.assertions = assertions;
  }

  public JenaIngestService(Dataset ds) {
    this.assertions = ds.asDatasetGraph();
  }

  @Override
  public String ingest(String ttl) {
    StringReader reader = new StringReader(ttl);
    Node graphNode = getGraphNode();
    Graph graph = GraphFactory.createDefaultGraph();
    RDFDataMgr.read(graph, reader, base, Lang.TTL);
    Txn.executeWrite(assertions, () -> assertions.addGraph(graphNode, graph));
    return graphNode.getURI();
  }
}
