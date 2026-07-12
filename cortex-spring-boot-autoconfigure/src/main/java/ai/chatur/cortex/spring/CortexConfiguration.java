package ai.chatur.cortex.spring;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.JenaCortex;
import java.io.IOException;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
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
@ComponentScan(basePackages = "ai.chatur.cortex.spring")
@EnableConfigurationProperties(CortexProperties.class)
public class CortexConfiguration {

  @Bean
  @Qualifier("assertions")
  Dataset assertions(CortexProperties properties) {
    if (properties.persistent()) return TDB2Factory.connectDataset(properties.assertionsLocation());
    else return TDB2Factory.createDataset();
  }

  @Bean
  OntModel ontModel(CortexProperties properties) throws IOException {
    OntModel ontModel = OntModelFactory.createModel();
    ontModel.read(properties.ontology().getInputStream(), null, "TTL");
    ontModel.lock();
    return ontModel;
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
  Cortex cortex(
      @Qualifier("assertions") Dataset assertions,
      OntModel ontModel,
      ShaclValidator shaclValidator,
      Shapes shapes) {
    return new JenaCortex(assertions, ontModel, shaclValidator, shapes);
  }
}
