package ai.chatur.cortex.spring.lint;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.lint.LintService;
import ai.chatur.cortex.spring.CortexProperties;
import java.io.IOException;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Configures linting and SHACL validation: the shapes merged from the configured Turtle resources,
 * the validator, the {@link LintService} checking assertions against the ontology and shapes, and
 * the MCP lint tool agents must call before ingesting.
 */
@Configuration
public class LintConfiguration {

  private static final Logger log = LoggerFactory.getLogger(LintConfiguration.class);

  @Bean
  ShaclValidator shaclValidator() {
    return ShaclValidator.get();
  }

  @Bean
  Shapes shapes(CortexProperties properties) throws IOException {
    Model model = ModelFactory.createDefaultModel();
    for (Resource resource : properties.shapes()) {
      RDFDataMgr.read(model, resource.getInputStream(), Lang.TTL);
      log.info("Loaded shapes from {}", resource);
    }
    Shapes shapes = Shapes.parse(model.getGraph());
    log.info("Parsed {} shapes", shapes.numRootShapes());
    return shapes;
  }

  @Bean
  LintService lintService(OntModel ontModel, ShaclValidator shaclValidator, Shapes shapes) {
    return new LintService(ontModel, shaclValidator, shapes);
  }

  @Bean
  LintTools lintTools(Cortex cortex) {
    return new LintTools(cortex);
  }
}
