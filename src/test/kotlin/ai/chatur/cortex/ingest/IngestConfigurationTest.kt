package ai.chatur.cortex.ingest

import ai.chatur.cortex.core.DatasetConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(classes = [DatasetConfiguration::class, IngestConfiguration::class])
@TestPropertySource(properties = ["cortex.ingest.enabled=true"])
class IngestConfigurationTest {

  @Autowired private lateinit var repository: IngestRepository
  @Autowired private lateinit var service: IngestService

  @Test fun contextLoads() {}
}
