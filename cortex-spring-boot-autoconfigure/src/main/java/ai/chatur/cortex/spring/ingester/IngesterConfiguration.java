package ai.chatur.cortex.spring.ingester;

import ai.chatur.cortex.IngestService;
import ai.chatur.cortex.JenaIngestService;
import org.apache.jena.query.Dataset;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngesterConfiguration {

  @Bean
  IngestService ingestService(@Qualifier("assertions") Dataset ds) {
    return new JenaIngestService(ds);
  }
}
