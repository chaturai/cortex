package ai.chatur.cortex.spring.stats;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.stats.StatsService;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.shacl.Shapes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures statistics: the {@link StatsService} computing them over the datasets, ontology,
 * shapes, and rules, and the web UI home page controller displaying them.
 */
@Configuration
public class StatsConfiguration {

  @Bean
  StatsService statsService(
      @Qualifier("assertions") Dataset assertions,
      @Qualifier("inferences") Dataset inferences,
      OntModel ontModel,
      Shapes shapes,
      GenericRuleReasoner genericRuleReasoner) {
    return new StatsService(
        assertions, inferences, ontModel, shapes, genericRuleReasoner.getRules());
  }

  @Bean
  StatsController statsController(Cortex cortex) {
    return new StatsController(cortex);
  }
}
