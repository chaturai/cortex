package ai.chatur.cortex.spring.lint;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.lint.LintService;
import org.apache.jena.ontapi.model.OntModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures linting: the {@link LintService} checking assertions against the ontology and the MCP
 * lint tool agents must call before ingesting.
 */
@Configuration
public class LintConfiguration {

  @Bean
  LintService lintService(OntModel ontModel) {
    return new LintService(ontModel);
  }

  @Bean
  LintTools lintTools(Cortex cortex) {
    return new LintTools(cortex);
  }
}
