package ai.chatur.cortex.ingest

import ai.chatur.cortex.core.DatasetConfiguration
import ai.chatur.cortex.core.DatasetService
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.sparql.graph.GraphFactory
import org.apache.jena.vocabulary.RDF
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(classes = [DatasetConfiguration::class, IngestConfiguration::class])
@TestPropertySource(properties = ["cortex.ingest.enabled=true"])
class IngestConfigurationTest {

  @Autowired private lateinit var repo: IngestRepository
  @Autowired private lateinit var ds: DatasetService

  private companion object {
    const val NS = "cortex://example/"

    fun String.toNode(): Node = NodeFactory.createURI(NS + this)
  }

  @Test
  fun `instances should be added to staging graphs`() {
    val graph =
        GraphFactory.createDefaultGraph().apply {
          add("Cortex".toNode(), RDF.type.asNode(), "Library".toNode())
        }
    ds.export(System.out)
    val graphName = repo.stage(graph)
    ds.export(System.out)
    repo.merge(graphName)
    ds.export(System.out)
    repo.merge(graphName, revert = true, debug = true)
    ds.export(System.out)
    repo.drop(graphName)
    ds.export(System.out)
  }
}
