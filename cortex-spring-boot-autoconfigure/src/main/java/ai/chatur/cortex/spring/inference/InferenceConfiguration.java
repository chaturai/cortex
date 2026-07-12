package ai.chatur.cortex.spring.inference;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.inference.InferenceService;
import ai.chatur.cortex.spring.CortexProperties;
import java.io.IOException;
import java.util.List;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures rule-based inference: a {@link GenericRuleReasoner} loaded from the configured rules
 * file and bound to the ontology, the {@link InferenceService} that applies it, and an initializer
 * that computes inference on startup.
 */
@Configuration
public class InferenceConfiguration {

  private static final Logger log = LoggerFactory.getLogger(InferenceConfiguration.class);

  @Bean
  GenericRuleReasoner genericRuleReasoner(CortexProperties properties) throws IOException {
    List<Rule> rules =
        GenericRuleReasoner.loadRules(properties.rules().getFile().getAbsolutePath());
    log.info("Loaded {} inference rules from {}", rules.size(), properties.rules());
    GenericRuleReasoner genericRuleReasoner = new GenericRuleReasoner(rules);
    genericRuleReasoner.setOWLTranslation(true);
    genericRuleReasoner.setTransitiveClosureCaching(true);
    return genericRuleReasoner;
  }

  @Bean
  InferenceService inferenceService(
      @Qualifier("assertions") Dataset assertions,
      @Qualifier("inferences") Dataset inferences,
      GenericRuleReasoner genericRuleReasoner,
      OntModel ontModel) {
    Reasoner reasoner = genericRuleReasoner.bindSchema(ontModel);
    return new InferenceService(assertions, inferences, reasoner);
  }

  @Bean
  InferenceInitializer inferenceInitializer(Cortex cortex) {
    return new InferenceInitializer(cortex);
  }
}
