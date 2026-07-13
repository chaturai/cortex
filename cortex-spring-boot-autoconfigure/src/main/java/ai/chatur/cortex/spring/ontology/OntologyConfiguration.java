package ai.chatur.cortex.spring.ontology;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.ontology.OntologyService;
import ai.chatur.cortex.spring.CortexProperties;
import java.io.IOException;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Configures the ontology: an immutable {@link OntModel} merged from the configured Turtle
 * resources, the {@link OntologyService} exposing it, the web UI controller, and the MCP resource
 * that serves the ontology to AI agents.
 */
@Configuration
public class OntologyConfiguration {

  private static final Logger log = LoggerFactory.getLogger(OntologyConfiguration.class);

  @Bean
  OntModel ontModel(CortexProperties properties) throws IOException {
    OntModel ontModel = OntModelFactory.createModel();
    for (Resource ontology : properties.ontology()) {
      ontModel.read(ontology.getInputStream(), null, "TTL");
      log.info("Loaded ontology from {}", ontology);
    }
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
