package ai.chatur.cortex.core

import org.apache.jena.query.Dataset
import org.apache.jena.sparql.core.DatasetGraph
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
  fun datasetGraph(ds: Dataset): DatasetGraph {
    return ds.asDatasetGraph()
  }

  @Bean
  fun datasetService(ds: Dataset): DatasetService {
    return DatasetService(ds)
  }
}
