package ai.chatur.cortex.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.OntologyClass;
import ai.chatur.cortex.Term;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex}'s ontology role: the raw ontology text and its class
 * hierarchy.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class OntologyTests {

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void getOntologyShouldReturnTheConfiguredOntology() {
    assertThat(cortex.getOntology())
        .as("the ontology contains the configured classes")
        .contains("Task", "Agent");
  }

  @Test
  void getClassHierarchyShouldEncodeClassesTheSameWayGetInstancesEncodesInstances() {
    // OntologyService.getOntologyClass uses the same Terms.of construction as
    // QueryService.listInstances (see query.QueryTests), so a prefixed ontology class IRI encodes
    // the same way a prefixed instance IRI does.
    List<OntologyClass> hierarchy = cortex.getClassHierarchy();

    assertThat(hierarchy.stream().map(OntologyClass::type))
        .as("ontology classes are encoded the same way instances are: Term(prefix, localName, uri)")
        .contains(new Term("", "Task", "example://ontology#Task"));
  }
}
