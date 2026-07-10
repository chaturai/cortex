package ai.chatur.cortex.ingest

import ai.chatur.cortex.core.DatasetConfiguration
import ai.chatur.cortex.reason.ReasonConfiguration
import org.apache.jena.riot.RDFDataMgr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(
    classes = [DatasetConfiguration::class, IngestConfiguration::class, ReasonConfiguration::class]
)
@TestPropertySource(
    properties =
        [
            "cortex.ingest.enabled=true",
            "cortex.ingest.ontologies[0]=cortex.ttl",
            "cortex.ingest.shapes[0]=shapes.ttl",
            "cortex.reason.enabled=true",
        ]
)
class IngestTest {

  @Autowired private lateinit var service: IngestService

  @Test
  fun `should stage valid instance`() {
    val graph = RDFDataMgr.loadGraph("instances/valid.ttl")
    val response = service.stage(graph)
    assertThat(response.valid).isTrue
    assertThat(response.graphName).isNotNull
    assertThat(response.errors).isNull()
  }

  @Test
  fun `should not stage invalid instance`() {
    val graph = RDFDataMgr.loadGraph("instances/invalid.ttl")
    val response = service.stage(graph)
    assertThat(response.valid).isFalse
    assertThat(response.graphName).isNull()
    assertThat(response.errors).isNotNull()
  }
}
