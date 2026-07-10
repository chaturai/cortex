package ai.chatur.cortex.query

import org.apache.jena.rdf.model.Model
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.query.enabled"])
class QueryConfiguration {
  @Bean
  fun queryRepository(@Qualifier("inference") model: Model): QueryRepository =
      QueryRepository(model, 1000)

  @Bean fun queryService(repository: QueryRepository): QueryService = QueryService(repository)
}
