package ai.chatur.cortex.spring.graph;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.CortexOntology;
import ai.chatur.cortex.CortexQuery;
import ai.chatur.cortex.OntologyClass;
import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.StatementOrigin;
import ai.chatur.cortex.Term;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/**
 * Plain JUnit tests for {@link GraphController}, against hand-rolled fakes of its two narrow
 * Phase-3 role dependencies ({@link CortexOntology}, {@link CortexQuery}) rather than a Spring
 * context — this is the payoff of narrowing the controller's constructor to just the roles it uses.
 */
class GraphControllerTests {

  private static final Term TASK_CLASS = new Term("", "Task", "example://ontology#Task");

  @Test
  void getAssertionsShouldRenderClassHierarchyWhenTypeIsNull() {
    FakeOntology ontology =
        new FakeOntology("unused", List.of(new OntologyClass(TASK_CLASS, List.of())));
    Term instance = new Term("kb", "ValidTask", "example://kb/ValidTask");
    FakeQuery query = new FakeQuery(Map.of(TASK_CLASS.uri(), List.of(instance)), Map.of());
    GraphController controller = new GraphController(ontology, query);
    Model model = new ExtendedModelMap();

    String view = controller.getAssertions(null, model);

    assertThat(view).isEqualTo("classes");
    @SuppressWarnings("unchecked")
    List<OntologyClass> classes = (List<OntologyClass>) model.getAttribute("classes");
    assertThat(classes).extracting(OntologyClass::type).contains(TASK_CLASS);
    @SuppressWarnings("unchecked")
    Map<String, Long> counts = (Map<String, Long>) model.getAttribute("counts");
    assertThat(counts)
        .as("the class hierarchy carries the instance count of each class")
        .containsEntry(TASK_CLASS.uri(), 1L);
  }

  @Test
  void getAssertionsShouldRenderInstancesWhenTypeIsGiven() {
    Term instance = new Term("kb", "ValidTask", "example://kb/ValidTask");
    FakeQuery query = new FakeQuery(Map.of("example://ontology#Task", List.of(instance)), Map.of());
    GraphController controller = new GraphController(new FakeOntology("unused", List.of()), query);
    Model model = new ExtendedModelMap();

    String view = controller.getAssertions("example://ontology#Task", model);

    assertThat(view).isEqualTo("instances");
    assertThat(model.getAttribute("type")).isEqualTo("example://ontology#Task");
    @SuppressWarnings("unchecked")
    List<Term> instances = (List<Term>) model.getAttribute("instances");
    assertThat(instances).contains(instance);
  }

  @Test
  void describeUriShouldRenderStatementsForTheGivenSubject() {
    ProvenancedStatement statement =
        new ProvenancedStatement(
            new Term("", "assignedTo", "example://ontology#assignedTo"),
            new Term("kb", "ValidAgent", "example://kb/ValidAgent"),
            "2024-01-01T00:00:00Z",
            StatementOrigin.ASSERTED,
            false,
            null,
            null);
    FakeQuery query = new FakeQuery(Map.of(), Map.of("example://kb/ValidTask", List.of(statement)));
    GraphController controller = new GraphController(new FakeOntology("unused", List.of()), query);
    Model model = new ExtendedModelMap();

    String view = controller.describeUri("example://kb/ValidTask", model);

    assertThat(view).isEqualTo("describe");
    assertThat(model.getAttribute("subject")).isEqualTo("example://kb/ValidTask");
    assertThat(model.getAttribute("statements")).isEqualTo(List.of(statement));
    assertThat(model.getAttribute("name"))
        .as(
            "the page renames a resource by substituting the name back into the IRI, so the name"
                + " has to be a suffix of it")
        .isEqualTo("ValidTask");
  }

  @Test
  void describeUriShouldNameAResourceByTheSuffixOfItsIri() {
    GraphController controller =
        new GraphController(
            new FakeOntology("unused", List.of()), new FakeQuery(Map.of(), Map.of()));

    assertThat(controller.getName("example://ontology#Task")).isEqualTo("Task");
    assertThat(controller.getName("example://kb/Task")).isEqualTo("Task");
    assertThat(controller.getName("urn:task")).isEqualTo("task");
    assertThat(controller.getName("Task"))
        .as("an IRI with no separator is its own name")
        .isEqualTo("Task");
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

  /** Hand-rolled fake of {@link CortexQuery}. */
  private record FakeQuery(
      Map<String, List<Term>> instancesByType,
      Map<String, List<ProvenancedStatement>> statementsByUri)
      implements CortexQuery {
    @Override
    public List<Term> getInstances(String type) {
      return instancesByType.getOrDefault(type, List.of());
    }

    @Override
    public Map<String, Long> countInstances() {
      return instancesByType.entrySet().stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  Map.Entry::getKey, entry -> (long) entry.getValue().size()));
    }

    @Override
    public List<ProvenancedStatement> describe(String id) {
      return statementsByUri.getOrDefault(id, List.of());
    }

    @Override
    public String query(String sparql) {
      throw new UnsupportedOperationException("not exercised by GraphController");
    }
  }
}
