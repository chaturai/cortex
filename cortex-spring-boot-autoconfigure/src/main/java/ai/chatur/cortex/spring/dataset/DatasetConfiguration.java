package ai.chatur.cortex.spring.dataset;

import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatasetConfiguration {

  @Bean
  @Qualifier("assertions")
  Dataset assertions() {
    return TDB2Factory.createDataset();
  }
}
