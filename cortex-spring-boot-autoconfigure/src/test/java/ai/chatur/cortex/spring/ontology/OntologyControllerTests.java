package ai.chatur.cortex.spring.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.CortexOntology;
import ai.chatur.cortex.OntologyClass;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/**
 * Plain JUnit test for {@link OntologyController}, against a hand-rolled fake of its single narrow
 * Phase-3 role dependency ({@link CortexOntology}) rather than a Spring context.
 *
 * <p>The prefix-mapping-immutability pin previously in this module's {@code OntologyUnitTests}
 * asserted on the {@code OntModel} bean directly, a Spring-wired implementation detail with no
 * Phase-3 role of its own; it now lives as {@code OntologyLoaderTests} in {@code cortex-core},
 * testing {@code OntologyLoader} — the class that actually locks the model — in isolation.
 */
class OntologyControllerTests {

  @Test
  void getOntologyShouldRenderTheOntologyTurtle() {
    String ontologyTurtle = "@prefix : <example://ontology#> .\n:Task a owl:Class .";
    OntologyController controller =
        new OntologyController(new FakeOntology(ontologyTurtle, List.of()));
    Model model = new ExtendedModelMap();

    String view = controller.getOntology(model);

    assertThat(view).isEqualTo("ontology");
    assertThat(model.getAttribute("ontology")).isEqualTo(ontologyTurtle);
  }

  /** Hand-rolled fake of {@link CortexOntology}. */
  private record FakeOntology(String ontology, List<OntologyClass> classHierarchy)
      implements CortexOntology {
    @Override
    public String getOntology() {
      return ontology;
    }

    @Override
    public List<OntologyClass> getClassHierarchy() {
      return classHierarchy;
    }
  }
}
