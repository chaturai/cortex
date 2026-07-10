package ai.chatur.cortex.core

import org.apache.jena.query.Dataset
import org.apache.jena.tdb2.TDB2Factory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DatasetConfiguration {

  @Bean
  fun dataset(): Dataset {
    return TDB2Factory.createDataset()
  }

  @Bean
  fun datasetService(ds: Dataset): DatasetService {
    return DatasetService(ds)
  }
}
