package ai.chatur.cortex.ingest

import ai.chatur.cortex.core.CortexGraphs.INSTANCES
import ai.chatur.cortex.core.CortexGraphs.getGraphNode
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.rdfpatch.RDFPatch
import org.apache.jena.rdfpatch.RDFPatchOps
import org.apache.jena.rdfpatch.changes.RDFChangesCollector
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.system.Txn

class IngestRepository(private val datasetGraph: DatasetGraph) {

  fun stage(graph: Graph): Node {
    val graphName = getGraphNode()
    Txn.executeWrite(datasetGraph) { datasetGraph.addGraph(graphName, graph) }
    return graphName
  }

  fun merge(graphName: Node, revert: Boolean = false): RDFPatch {
    val collector = RDFChangesCollector()
    collector.txnBegin()
    Txn.executeRead(datasetGraph) {
      datasetGraph.getGraph(graphName).stream().forEach {
        if (revert) collector.delete(INSTANCES, it.subject, it.predicate, it.`object`)
        else collector.add(INSTANCES, it.subject, it.predicate, it.`object`)
      }
    }
    collector.txnCommit()
    val patch = collector.rdfPatch
    RDFPatchOps.applyChange(datasetGraph, patch)
    return patch
  }

  fun drop(graphName: Node) {
    Txn.executeWrite(datasetGraph) { datasetGraph.removeGraph(graphName) }
  }
}
