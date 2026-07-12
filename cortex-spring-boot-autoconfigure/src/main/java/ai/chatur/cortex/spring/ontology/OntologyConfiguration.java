package ai.chatur.cortex.spring.ontology;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.ontology.OntologyService;
import ai.chatur.cortex.spring.CortexProperties;
import java.io.IOException;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OntologyConfiguration {

  @Bean
  OntModel ontModel(CortexProperties properties) throws IOException {
    OntModel ontModel = OntModelFactory.createModel();
    ontModel.read(properties.ontology().getInputStream(), null, "TTL");
    ontModel.lock();
    return ontModel;
  }

  @Bean
  OntologyService ontologyService(OntModel ontModel) {
    return new OntologyService(ontModel);
  }

  @Bean
  OntologyController ontologyController(Cortex cortex) {
    return new OntologyController(cortex);
  }

  @Bean
  OntologyResources ontologyResources(Cortex cortex) {
    return new OntologyResources(cortex);
  }
}
