package ai.chatur.cortex.core

import java.util.UUID
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory

object CortexGraphs {
  const val NS = "cortex://graph/"
  val INSTANCES = getGraphNode("instances")

  fun getGraphNode(id: String = UUID.randomUUID().toString()): Node =
      NodeFactory.createURI(NS + id.removePrefix(NS))
}
