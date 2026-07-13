package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.ingest.IngestService;
import ai.chatur.cortex.core.lint.LintService;
import ai.chatur.cortex.spring.CortexProperties;
import java.io.IOException;
import org.apache.jena.query.Dataset;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures ingestion: the TDB2 dataset holding assertions (in-memory or persistent, per the
 * {@code cortex.persistent} property), SHACL validation, the {@link IngestService} (which lints
 * incoming assertions against the ontology before validating them), the web UI controller for
 * reviewing branches, and the MCP ingest tool.
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
  ShaclValidator shaclValidator() {
    return ShaclValidator.get();
  }

  @Bean
  Shapes shapes(CortexProperties properties) throws IOException {
    Shapes shapes = Shapes.parse(properties.shapes().getFile().getAbsolutePath());
    log.info("Loaded {} shapes from {}", shapes.numRootShapes(), properties.shapes());
    return shapes;
  }

  @Bean
  IngestService ingestService(
      @Qualifier("assertions") Dataset assertions,
      LintService lintService,
      ShaclValidator shaclValidator,
      Shapes shapes) {
    return new IngestService(assertions, lintService, shaclValidator, shapes);
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
