package ai.chatur.cortex.spring.ontology;

import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.spring.CortexConfiguration;
import java.io.IOException;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.shared.PrefixMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@SpringJUnitConfig(CortexConfiguration.class)
public class OntologyUnitTests {

  @Autowired OntModel ontModel;

  @Autowired Cortex cortex;

  @Autowired OntologyController ontologyController;

  @Test
  void ontologyPrefixMappingShouldBeImmutable() {
    assertThrows(
        PrefixMapping.JenaLockedException.class,
        () -> ontModel.setNsPrefix("", "cortex://ontology"));
  }

  @Test
  void controllerShouldRenderOntologyView() throws IOException {
    Model model = new ExtendedModelMap();
    String view = ontologyController.getOntology(model);
    assert (view.equals("ontology"));
    assert (cortex.getOntology().equals(model.getAttribute("ontology")));
  }
}
