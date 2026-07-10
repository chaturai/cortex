package ai.chatur.cortex.core

import org.apache.jena.query.Dataset
import org.apache.jena.tdb2.TDB2Factory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(DatasetConfiguration::class)
class DatasetConfigurationTest {

  @Autowired private lateinit var ds: Dataset

  @Test
  fun `dataset should be TDB2 compliant`() {
    assert(TDB2Factory.isTDB2(ds))
  }
}
