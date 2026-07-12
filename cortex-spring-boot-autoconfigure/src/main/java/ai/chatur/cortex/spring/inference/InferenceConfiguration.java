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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InferenceConfiguration {

  @Bean
  GenericRuleReasoner genericRuleReasoner(CortexProperties properties) throws IOException {
    List<Rule> rules =
        GenericRuleReasoner.loadRules(properties.rules().getFile().getAbsolutePath());
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
