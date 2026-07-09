package ai.chatur.cortex.core

import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory

class CortexResourceFactory(dataset: Dataset) {

  private val graphNamespace = dataset.prefixMapping.getNsPrefixURI("g")
  private val ontologyNamespace = dataset.prefixMapping.getNsPrefixURI("o")
  private val instanceNamespace = dataset.prefixMapping.getNsPrefixURI("i")

  fun getGraph(id: String): Resource = ResourceFactory.createResource(graphNamespace + id)

  fun getOntology(id: String): Resource = ResourceFactory.createResource(ontologyNamespace + id)

  fun getInstance(id: String): Resource = ResourceFactory.createResource(instanceNamespace + id)
}
