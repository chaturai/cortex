package ai.chatur.cortex.spring;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.JenaCortex;
import ai.chatur.cortex.core.inference.InferenceService;
import ai.chatur.cortex.core.ingest.IngestService;
import ai.chatur.cortex.core.ontology.OntologyService;
import ai.chatur.cortex.core.query.QueryService;
import ai.chatur.cortex.core.stats.StatsService;
import ai.chatur.cortex.spring.inference.InferenceConfiguration;
import ai.chatur.cortex.spring.ingest.IngestConfiguration;
import ai.chatur.cortex.spring.ontology.OntologyConfiguration;
import ai.chatur.cortex.spring.query.QueryConfiguration;
import ai.chatur.cortex.spring.stats.StatsConfiguration;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.system.Txn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration entry point for Cortex.
 *
 * <p>Assembles a ready-to-use {@link Cortex} knowledge graph from the ontology, ingest, query, and
 * inference configurations, driven by the {@code cortex.*} {@link CortexProperties properties}.
 */
@Configuration
@EnableConfigurationProperties(CortexProperties.class)
@Import({
  OntologyConfiguration.class,
  IngestConfiguration.class,
  QueryConfiguration.class,
  InferenceConfiguration.class,
  StatsConfiguration.class
})
public class CortexConfiguration {

  @Bean
  Cortex cortex(
      OntologyService ontologyService,
      IngestService ingestService,
      InferenceService inferenceService,
      QueryService queryService,
      StatsService statsService,
      @Qualifier("assertions") Dataset assertions,
      OntModel ontModel) {
    Txn.executeWrite(
        assertions, () -> assertions.getDefaultModel().setNsPrefixes(ontModel.getNsPrefixMap()));
    return new JenaCortex(
        ontologyService, ingestService, inferenceService, queryService, statsService);
  }
}
