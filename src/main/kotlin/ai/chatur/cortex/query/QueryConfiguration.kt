package ai.chatur.cortex.query

import org.apache.jena.query.Dataset
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.query.enabled"])
class QueryConfiguration {
  @Bean fun queryRepository(dataset: Dataset): QueryRepository = QueryRepository(dataset, 1000)

  @Bean fun queryService(repository: QueryRepository): QueryService = QueryService(repository)
}
