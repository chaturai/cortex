package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.ingest.IngestService;
import ai.chatur.cortex.core.lint.LintService;
import ai.chatur.cortex.spring.CortexProperties;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures ingestion: the TDB2 dataset holding assertions (in-memory or persistent, per the
 * {@code cortex.persistent} property), the {@link IngestService} (which lints and validates
 * incoming assertions through the lint service), the web UI controller for reviewing branches, and
 * the MCP ingest tool.
 */
@Configuration
public class IngestConfiguration {

  private static final Logger log = LoggerFactory.getLogger(IngestConfiguration.class);

  @Bean
  @Qualifier("assertions")
  Dataset assertions(CortexProperties properties) {
    if (properties.persistent()) {
      log.info("Connecting persistent assertions store at {}", properties.assertionsLocation());
      return TDB2Factory.connectDataset(properties.assertionsLocation());
    }
    log.info("Using in-memory assertions store");
    return TDB2Factory.createDataset();
  }

  @Bean
  IngestService ingestService(
      @Qualifier("assertions") Dataset assertions, LintService lintService, OntModel ontModel) {
    return new IngestService(assertions, lintService, ontModel);
  }

  @Bean
  IngestController ingestController(Cortex cortex) {
    return new IngestController(cortex);
  }

  @Bean
  IngestTools ingestTools(Cortex cortex) {
    return new IngestTools(cortex);
  }
}
