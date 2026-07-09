package ai.chatur.cortex.query

import ai.chatur.cortex.core.DatasetConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(classes = [DatasetConfiguration::class, QueryConfiguration::class])
@TestPropertySource(properties = ["cortex.query.enabled=true"])
class QueryConfigurationTest {

  @Autowired private lateinit var repository: QueryRepository
  @Autowired private lateinit var service: QueryService

  @Test fun contextLoads() {}
}
