package ai.chatur.cortex.reason

import ai.chatur.cortex.core.DatasetConfiguration
import ai.chatur.cortex.ingest.IngestConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(
    classes = [DatasetConfiguration::class, ReasonConfiguration::class, IngestConfiguration::class]
)
@TestPropertySource(properties = ["cortex.reason.enabled=true", "cortex.ingest.enabled=true"])
class ReasonTest {

  @Autowired private lateinit var service: ReasonService

  @Test fun contextLoads() {}
}
