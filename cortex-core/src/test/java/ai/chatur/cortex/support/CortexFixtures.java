package ai.chatur.cortex.support;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.CortexBuilder;
import java.util.List;

/**
 * Shared ontology/shapes/rules fixture for core behavior tests, and a factory for a fresh, fully
 * in-memory {@link Cortex} built from it.
 *
 * <p>Content mirrors {@code cortex-spring-boot-autoconfigure/src/test/resources/ontology.ttl},
 * {@code ontology.shapes}, and {@code ontology.rules} so the two modules exercise the same domain.
 *
 * <p>Every test class using {@link #newCortex()} gets its own isolated graph — typically built in a
 * {@code @BeforeEach} — so tests may use small, stable, human-readable IRIs instead of
 * UUID-suffixed ones: with isolation, there is no shared dataset for two tests to collide on.
 */
public final class CortexFixtures {

  public static final String ONTOLOGY =
      """
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .

      @prefix : <example://ontology#> .
      @prefix kb: <example://kb/> .

      :Task a owl:Class .

      :Agent a owl:Class .

      :assignedTo a owl:ObjectProperty ;
          rdfs:domain :Task ;
          rdfs:range :Agent .
      """;

  public static final String SHAPES =
      """
      @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      @prefix sh:   <http://www.w3.org/ns/shacl#> .
      @prefix prov: <http://www.w3.org/ns/prov#> .

      @prefix : <example://ontology#> .
      @prefix s: <example://shapes#> .

      s:UnknownShape a sh:NodeShape ;
          sh:targetSubjectsOf rdf:type ;
          sh:property [
              sh:path rdf:type ;
              sh:in ( :Task :Agent prov:Activity ) ;
              sh:message "Instance uses a class that does not belong to the ontology." ;
          ] .

      s:TaskShape a sh:NodeShape ;
          sh:targetClass :Task ;
          sh:property [
              sh:path :assignedTo ;
              sh:class :Agent ;
              sh:minCount 1 ;
              sh:message "a task must be assigned to at least one agent" ;
          ] .
      """;

  public static final String RULES =
      """
      @prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      [subClass: (?x rdf:type ?c1) (?c1 rdfs:subClassOf ?c2) -> (?x rdf:type ?c2)]

      [domain: (?p rdfs:domain ?c) (?x ?p ?y) -> (?x rdf:type ?c)]

      [range: (?p rdfs:range ?c) (?x ?p ?y) -> (?y rdf:type ?c)]
      """;

  private CortexFixtures() {}

  /**
   * Builds a fresh, fully in-memory {@link Cortex} over {@link #ONTOLOGY}, {@link #SHAPES}, and
   * {@link #RULES}.
   *
   * @return the freshly built, isolated knowledge graph
   */
  public static Cortex newCortex() {
    return CortexBuilder.create()
        .ontologies(List.of(ONTOLOGY))
        .shapes(List.of(SHAPES))
        .rules(List.of(RULES))
        .build();
  }
}
