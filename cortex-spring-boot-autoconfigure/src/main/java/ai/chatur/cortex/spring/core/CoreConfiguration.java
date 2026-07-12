package ai.chatur.cortex.spring.core;

import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "ai.chatur.cortex.spring.core")
@EnableConfigurationProperties(CoreProperties.class)
public class CoreConfiguration {

  @Bean
  @Qualifier("assertions")
  Dataset assertions(CoreProperties properties) {
    if (properties.persistent()) return TDB2Factory.connectDataset(properties.location() + "/db");
    else return TDB2Factory.createDataset();
  }
}
