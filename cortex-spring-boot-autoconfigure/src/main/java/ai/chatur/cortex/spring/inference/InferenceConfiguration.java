package ai.chatur.cortex.spring.inference;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.inference.InferenceService;
import ai.chatur.cortex.spring.CortexProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Configures rule-based inference: a {@link GenericRuleReasoner} loaded from the configured rules
 * files, the {@link InferenceService} that applies the OWL-Full closure of the ontology followed
 * by those rules, and an initializer that computes inference on startup.
 */
@Configuration
public class InferenceConfiguration {

  private static final Logger log = LoggerFactory.getLogger(InferenceConfiguration.class);

  @Bean
  GenericRuleReasoner genericRuleReasoner(CortexProperties properties) throws IOException {
    List<Rule> rules = new ArrayList<>();
    for (Resource resource : properties.rules()) {
      // Read via the input stream: Resource.getFile() fails for classpath resources inside a jar
      List<Rule> loaded = Rule.parseRules(resource.getContentAsString(StandardCharsets.UTF_8));
      log.info("Loaded {} inference rules from {}", loaded.size(), resource);
      rules.addAll(loaded);
    }
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
    return new InferenceService(assertions, inferences, genericRuleReasoner, ontModel);
  }

  @Bean
  InferenceInitializer inferenceInitializer(Cortex cortex) {
    return new InferenceInitializer(cortex);
  }
}
