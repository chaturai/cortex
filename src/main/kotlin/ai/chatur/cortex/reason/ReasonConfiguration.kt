package ai.chatur.cortex.reason

import org.apache.jena.query.Dataset
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.reason.enabled"])
class ReasonConfiguration {
  @Bean fun reasonRepository(dataset: Dataset): ReasonRepository = ReasonRepository(dataset)

  @Bean fun reasonService(repository: ReasonRepository): ReasonService = ReasonService(repository)
}
