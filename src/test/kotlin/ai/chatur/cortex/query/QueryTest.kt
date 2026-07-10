package ai.chatur.cortex.query

import ai.chatur.cortex.core.DatasetConfiguration
import ai.chatur.cortex.ingest.IngestConfiguration
import ai.chatur.cortex.ingest.IngestService
import ai.chatur.cortex.reason.ReasonConfiguration
import org.apache.jena.query.QueryFactory
import org.apache.jena.riot.RDFDataMgr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(
    classes =
        [
            DatasetConfiguration::class,
            QueryConfiguration::class,
            IngestConfiguration::class,
            ReasonConfiguration::class,
        ]
)
@TestPropertySource(
    properties =
        [
            "cortex.query.enabled=true",
            "cortex.ingest.enabled=true",
            "cortex.reason.enabled=true",
            "cortex.reason.rules[0]=cortex.rules",
            "cortex.ingest.ontologies[0]=cortex.ttl",
            "cortex.ingest.shapes[0]=shapes.ttl",
        ]
)
class QueryTest {

  @Autowired private lateinit var repository: QueryRepository
  @Autowired private lateinit var service: QueryService
  @Autowired private lateinit var ingestService: IngestService

  @Test
  fun `ask should use inference graph`() {
    val graph = RDFDataMgr.loadGraph("instances/valid.ttl")
    val response = ingestService.stage(graph)
    val query =
        QueryFactory.create(
            """
            PREFIX o: <cortex://ontologies/>
            PREFIX i: <cortex://instances/>

            ASK {
              i:ValidTask a o:Task .
            }
            """
                .trimIndent()
        )
    assertThat(repository.ask(query)).isFalse
    ingestService.approve(response.graphName!!)
    assertThat(repository.ask(query)).isTrue
  }
}
