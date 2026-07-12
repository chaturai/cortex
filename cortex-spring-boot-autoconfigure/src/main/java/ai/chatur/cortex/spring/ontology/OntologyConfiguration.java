package ai.chatur.cortex.spring.ontology;

import ai.chatur.cortex.JenaOntologyRepository;
import ai.chatur.cortex.OntologyRepository;
import java.io.IOException;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OntologyProperties.class)
public class OntologyConfiguration {

  @Bean
  OntModel ontModel(OntologyProperties properties) throws IOException {
    OntModel ontModel = OntModelFactory.createModel();
    ontModel.read(properties.path().getInputStream(), properties.base(), properties.format());
    ontModel.lock();
    return ontModel;
  }

  @Bean
  OntologyRepository ontologyRepository(OntModel ontModel) {
    return new JenaOntologyRepository(ontModel);
  }
}
