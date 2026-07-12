package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.IngestService;
import ai.chatur.cortex.JenaIngestService;
import java.io.IOException;
import org.apache.jena.query.Dataset;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "ai.chatur.cortex.spring.ingest")
@EnableConfigurationProperties({IngestProperties.class})
public class IngestConfiguration {

  @Bean
  @Qualifier("assertions")
  Dataset assertions(IngestProperties properties) {
    if (properties.persistent()) return TDB2Factory.connectDataset(properties.location());
    else return TDB2Factory.createDataset();
  }

  @Bean
  IngestService ingestService(@Qualifier("assertions") Dataset ds, IngestProperties properties)
      throws IOException {
    ShaclValidator shaclValidator = ShaclValidator.get();
    Shapes shapes = Shapes.parse(properties.shapes().getFile().getAbsolutePath());
    return new JenaIngestService(ds, shaclValidator, shapes);
  }
}
