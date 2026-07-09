package ai.chatur.cortex.core

import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory

class CortexResourceFactory(dataset: Dataset) {

  private val GRAPH = dataset.prefixMapping.getNsPrefixURI("g")
  private val ONTOLOGY = dataset.prefixMapping.getNsPrefixURI("o")
  private val DATA = dataset.prefixMapping.getNsPrefixURI("")

  fun getGraph(id: String): Resource = ResourceFactory.createResource(GRAPH + id)

  fun getOntology(id: String): Resource = ResourceFactory.createResource(ONTOLOGY + id)

  fun getData(id: String): Resource = ResourceFactory.createResource(DATA + id)
}
