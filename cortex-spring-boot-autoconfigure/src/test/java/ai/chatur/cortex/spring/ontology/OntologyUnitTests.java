package ai.chatur.cortex.spring.ontology;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.shared.PrefixMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(OntologyConfiguration.class)
public class OntologyUnitTests {

  @Autowired OntModel ontModel;

  @Test
  void ontologyPrefixMappingShouldBeImmutable() {
    assertThrows(
        PrefixMapping.JenaLockedException.class,
        () -> ontModel.setNsPrefix("", "cortex://ontology"));
  }
}
