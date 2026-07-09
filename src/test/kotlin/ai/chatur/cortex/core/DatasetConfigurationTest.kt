package ai.chatur.cortex.core

import org.apache.jena.query.Dataset
import org.apache.jena.tdb2.TDB2Factory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(DatasetConfiguration::class)
class DatasetConfigurationTest {

  @Autowired private lateinit var dataset: Dataset

  @Test
  fun `dataset must be TDB2`() {
    assertThat(TDB2Factory.isTDB2(dataset)).isTrue()
  }
}
