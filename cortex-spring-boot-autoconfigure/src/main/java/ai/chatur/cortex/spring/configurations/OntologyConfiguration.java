package ai.chatur.cortex.spring.configurations;

import ai.chatur.cortex.JenaOntologyRepository;
import ai.chatur.cortex.OntologyRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OntologyConfiguration {

    @Bean
    OntologyRepository ontologyRepository() {
        return new JenaOntologyRepository();
    }
}
