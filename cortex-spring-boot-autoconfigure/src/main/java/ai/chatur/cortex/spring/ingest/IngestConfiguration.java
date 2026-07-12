package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.ingest.IngestService;
import ai.chatur.cortex.spring.CortexProperties;
import java.io.IOException;
import org.apache.jena.query.Dataset;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestConfiguration {

  @Bean
  @Qualifier("assertions")
  Dataset assertions(CortexProperties properties) {
    if (properties.persistent()) return TDB2Factory.connectDataset(properties.assertionsLocation());
    else return TDB2Factory.createDataset();
  }

  @Bean
  ShaclValidator shaclValidator() {
    return ShaclValidator.get();
  }

  @Bean
  Shapes shapes(CortexProperties properties) throws IOException {
    return Shapes.parse(properties.shapes().getFile().getAbsolutePath());
  }

  @Bean
  IngestService ingestService(
      @Qualifier("assertions") Dataset assertions, ShaclValidator shaclValidator, Shapes shapes) {
    return new IngestService(assertions, shaclValidator, shapes);
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
