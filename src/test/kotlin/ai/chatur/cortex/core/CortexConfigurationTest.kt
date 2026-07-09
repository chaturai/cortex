package ai.chatur.cortex.core

import kotlin.test.assertContains
import org.apache.jena.query.Dataset
import org.apache.jena.system.Txn
import org.apache.jena.vocabulary.OWL2
import org.apache.jena.vocabulary.RDF
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(CortexConfiguration::class)
class CortexConfigurationTest {

  @Autowired private lateinit var dataset: Dataset

  @Autowired private lateinit var resourceFactory: CortexResourceFactory

  @Test
  fun `dataset default model contains classpath ontologies`() {
    Txn.executeRead(dataset) {
      dataset.defaultModel.apply {
        assert(contains(resourceFactory.getOntology("Task"), RDF.type, OWL2.Class))
      }
    }
  }

  @Test
  fun `dataset contains reserved named graphs`() {
    Txn.executeRead(dataset) {
      val namedGraphs = dataset.listModelNames().asSequence().toList()
      assertContains(namedGraphs, resourceFactory.getGraph("instances"))
      assertContains(namedGraphs, resourceFactory.getGraph("inferences"))
    }
  }
}
