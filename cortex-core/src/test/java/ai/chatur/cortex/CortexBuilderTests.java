package ai.chatur.cortex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Smoke test proving {@link CortexBuilder} assembles a working, fully in-memory {@link Cortex}
 * without a Spring context.
 *
 * <p>This is deliberately narrow: it exercises ingest, approve, and query once each to prove the
 * builder wires the core services together correctly. It is not a substitute for the behavior
 * coverage that belongs to each service's own tests.
 */
class CortexBuilderTests {

  private static final String ONTOLOGY =
      """
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix : <example://ontology#> .

      :Task a owl:Class .
      :Agent a owl:Class .
      :assignedTo a owl:ObjectProperty ;
          rdfs:domain :Task ;
          rdfs:range :Agent .
      """;

  private static final String SHAPES =
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
          ] .
      """;

  private static final String RULES =
      """
      @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      [domain: (?p rdfs:domain ?c) (?x ?p ?y) -> (?x rdf:type ?c)]
      [range: (?p rdfs:range ?c) (?x ?p ?y) -> (?y rdf:type ?c)]
      """;

  private static final String ASSERTIONS =
      """
      @prefix : <example://ontology#> .
      @prefix kb: <example://kb/> .

      kb:ValidTask :assignedTo kb:ValidAgent .
      """;

  @Test
  void buildsIngestsApprovesAndQueriesAnInMemoryGraph() {
    Cortex cortex =
        CortexBuilder.create()
            .ontologies(List.of(ONTOLOGY))
            .shapes(List.of(SHAPES))
            .rules(List.of(RULES))
            .build();

    IngestResult ingested = cortex.ingest(ASSERTIONS);
    assertThat(ingested.valid()).isTrue();
    assertThat(ingested.branch()).isNotNull();

    cortex.approve(ingested.branch());
    assertThat(cortex.hasBranch(ingested.branch())).isFalse();

    List<Term> tasks = cortex.getInstances("example://ontology#Task");
    assertThat(tasks).extracting(Term::uri).containsExactly("example://kb/ValidTask");

    List<Term> agents = cortex.getInstances("example://ontology#Agent");
    assertThat(agents).extracting(Term::uri).containsExactly("example://kb/ValidAgent");
  }
}
