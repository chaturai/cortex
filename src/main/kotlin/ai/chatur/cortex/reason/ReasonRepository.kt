package ai.chatur.cortex.reason

import ai.chatur.cortex.core.CortexGraphs.INSTANCES
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.Reasoner
import org.apache.jena.system.Txn

class ReasonRepository(
    private val dataset: Dataset,
    private val reasoner: Reasoner,
    private val inf: Model,
) {

  fun computeInference() {
    Txn.executeRead(dataset) {
      val model = dataset.getNamedModel(INSTANCES.uri)
      inf.add(ModelFactory.createInfModel(reasoner, model))
    }
  }
}
