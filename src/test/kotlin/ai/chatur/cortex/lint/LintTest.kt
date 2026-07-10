package ai.chatur.cortex.lint

import ai.chatur.cortex.core.DatasetConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(classes = [DatasetConfiguration::class, LintConfiguration::class])
@TestPropertySource(properties = ["cortex.lint.enabled=true"])
class LintTest {

  @Autowired private lateinit var service: LintService

  @Test fun contextLoads() {}
}
