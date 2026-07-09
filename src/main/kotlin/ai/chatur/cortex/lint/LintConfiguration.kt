package ai.chatur.cortex.lint

import org.apache.jena.query.Dataset
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.lint.enabled"])
class LintConfiguration {

  @Bean fun lintRepository(dataset: Dataset): LintRepository = LintRepository(dataset)

  @Bean fun lintService(repository: LintRepository): LintService = LintService(repository)
}
