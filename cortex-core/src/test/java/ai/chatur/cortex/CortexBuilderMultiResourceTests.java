package ai.chatur.cortex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves that {@link CortexBuilder#ontologies(List)}, {@link CortexBuilder#shapes(List)}, and
 * {@link CortexBuilder#rules(List)} genuinely merge <strong>multiple</strong> documents rather than
 * only ever exercising a single one — the headline "documented multi-resource" feature ({@code
 * README.md:14}: {@code cortex.ontologies}/{@code cortex.shapes}/{@code cortex.rules} each bind a
 * list) that, before this class, every other test left untested by always supplying exactly one
 * document per list.
 */
class CortexBuilderMultiResourceTests {

  /**
   * Defines only {@code :Task} and {@code :assignedTo}; {@code :Agent} lives in {@link
   * #ONTOLOGY_B}.
   */
  private static final String ONTOLOGY_A =
      """
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix : <example://ontology#> .

      :Task a owl:Class .

      :assignedTo a owl:ObjectProperty ;
          rdfs:domain :Task ;
          rdfs:range :Agent .
      """;

  /** Defines only {@code :Agent}, the range class {@link #ONTOLOGY_A}'s property refers to. */
  private static final String ONTOLOGY_B =
      """
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix : <example://ontology#> .

      :Agent a owl:Class .
      """;

  /** Requires every {@code :Task} to have at least one {@code :assignedTo}. */
  private static final String SHAPES_A =
      """
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix : <example://ontology#> .
      @prefix s: <example://shapes#> .

      s:TaskShape a sh:NodeShape ;
          sh:targetClass :Task ;
          sh:property [
              sh:path :assignedTo ;
              sh:class :Agent ;
              sh:minCount 1 ;
              sh:message "a task must be assigned to at least one agent" ;
          ] .
      """;

  /** Independently requires every {@code :Agent} to have an {@code rdfs:label}. */
  private static final String SHAPES_B =
      """
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix : <example://ontology#> .
      @prefix s: <example://shapes#> .

      s:AgentShape a sh:NodeShape ;
          sh:targetClass :Agent ;
          sh:property [
              sh:path rdfs:label ;
              sh:minCount 1 ;
              sh:message "an agent must have an rdfs:label" ;
          ] .
      """;

  /** Infers {@code rdf:type :Task} from the domain of {@code :assignedTo}. */
  private static final String RULES_A =
      """
      @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      [domain: (?p rdfs:domain ?c) (?x ?p ?y) -> (?x rdf:type ?c)]
      """;

  /** Independently infers {@code rdf:type :Agent} from the range of {@code :assignedTo}. */
  private static final String RULES_B =
      """
      @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      [range: (?p rdfs:range ?c) (?x ?p ?y) -> (?y rdf:type ?c)]
      """;

  private static Cortex newCortex() {
    return CortexBuilder.create()
        .ontologies(List.of(ONTOLOGY_A, ONTOLOGY_B))
        .shapes(List.of(SHAPES_A, SHAPES_B))
        .rules(List.of(RULES_A, RULES_B))
        .build();
  }

  @Test
  void twoOntologyDocumentsShouldMergeIntoOneOntology() {
    Cortex cortex = newCortex();

    // Neither document alone defines both :Task and :Agent; if the merge dropped one of the two
    // documents, linting a statement that needs classes/properties from both would fail.
    IngestResult ingested =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix kb: <example://kb/> .

            kb:MergeTask a :Task ;
                :assignedTo kb:MergeAgent .

            kb:MergeAgent a :Agent ;
                rdfs:label "Merge Agent" .
            """);

    assertThat(ingested.valid())
        .as("lint only passes if :Task (ontology A) and :Agent (ontology B) are both known")
        .isTrue();
    assertThat(ingested.errors()).isNull();
  }

  @Test
  void bothShapesDocumentsShouldBeEnforcedIndependently() {
    Cortex cortex = newCortex();

    // Violates only SHAPES_A (:Task needs :assignedTo); the agent carries a label, so SHAPES_B is
    // satisfied here.
    IngestResult missingAssignedTo =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:UnassignedTask a :Task .
            """);
    assertThat(missingAssignedTo.valid()).as("SHAPES_A's TaskShape fires").isFalse();
    assertThat(missingAssignedTo.errors())
        .contains("a task must be assigned to at least one agent");

    // Violates only SHAPES_B (:Agent needs an rdfs:label); the task-side assignment is fine.
    IngestResult missingLabel =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:LabeledTask a :Task ;
                :assignedTo kb:UnlabeledAgent .
            kb:UnlabeledAgent a :Agent .
            """);
    assertThat(missingLabel.valid()).as("SHAPES_B's AgentShape fires").isFalse();
    assertThat(missingLabel.errors()).contains("an agent must have an rdfs:label");
  }

  @Test
  void bothRulesDocumentsShouldFireIndependently() {
    Cortex cortex = newCortex();
    IngestResult ingested =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix kb: <example://kb/> .

            kb:RuleTask :assignedTo kb:RuleAgent .
            kb:RuleAgent rdfs:label "Rule Agent" .
            """);
    cortex.approve(ingested.branch());

    // RULES_A infers kb:RuleTask a :Task (from the domain of :assignedTo); RULES_B independently
    // infers kb:RuleAgent a :Agent (from its range). Neither type was asserted directly.
    assertThat(cortex.getInstances("example://ontology#Task"))
        .as("RULES_A's domain rule fired")
        .extracting(Term::uri)
        .contains("example://kb/RuleTask");
    assertThat(cortex.getInstances("example://ontology#Agent"))
        .as("RULES_B's range rule fired")
        .extracting(Term::uri)
        .contains("example://kb/RuleAgent");
  }
}
