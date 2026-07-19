package ai.chatur.cortex.spring.example;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.CortexBuilder;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.LintResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Pins the SKOS tutorial published at {@code site/skos-tutorial.html}, which builds a controlled
 * vocabulary on the SKOS Core standard rather than a hand-written ontology.
 *
 * <p>The ontology is this module's real {@code skos.ttl}; the shapes and rules are {@code
 * skos-tutorial.shapes} and {@code skos-tutorial.rules} in this module's test resources, and are
 * the same files the tutorial prints verbatim. That is the point of the class: {@code
 * cortex-schema-plugin} has no CI and its instructions drifted out of step with the code once
 * already, and the site is the same kind of hazard — instructions nothing executes. Change the
 * tutorial's shapes, rules, or sample Turtle and this fails.
 *
 * <p>Each test builds its own fully in-memory graph, so the ingest-and-approve cases cannot see
 * each other's approvals.
 */
class SkosTutorialTests {

  /** The sample vocabulary the tutorial asks an agent to ingest. */
  private static final String TTL =
      """
      @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
      @prefix : <https://example.org/vocab/> .

      :topics a skos:ConceptScheme ;
          skos:prefLabel "Documentation topics" .

      :knowledge-graph a skos:Concept ;
          skos:prefLabel "Knowledge graph" ;
          skos:definition "A graph-structured store of facts about entities and their relationships." ;
          skos:topConceptOf :topics .

      :ontology a skos:Concept ;
          skos:prefLabel "Ontology" ;
          skos:definition "A formal vocabulary of classes and properties for a domain." ;
          skos:broader :knowledge-graph .

      :shacl a skos:Concept ;
          skos:prefLabel "SHACL" ;
          skos:altLabel "Shapes Constraint Language" ;
          skos:definition "A language for validating the structure of RDF data." ;
          skos:broader :ontology ;
          skos:related :ontology .
      """;

  private Cortex cortex;

  @BeforeEach
  void setUp() throws IOException {
    cortex =
        CortexBuilder.create()
            .ontologies(List.of(resource("skos.ttl")))
            .shapes(List.of(resource("skos-tutorial.shapes")))
            .rules(List.of(resource("skos-tutorial.rules")))
            .build();
  }

  private static String resource(String name) throws IOException {
    return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void shouldStageTheTutorialVocabulary() {
    IngestResult result = cortex.ingest(TTL);

    assertThat(result.errors()).isNull();
    assertThat(result.valid())
        .as("the sample vocabulary the tutorial tells the reader to ingest must actually ingest")
        .isTrue();
    assertThat(result.branch()).isNotNull();
  }

  @Test
  void shouldInferTransitiveAncestryAndTheInverseOfBroader() {
    // The tutorial's headline claim: skos:broader is a sub-property of skos:broaderTransitive in
    // SKOS itself, so the broaderTransitive rule only has to chain -- :shacl reaches
    // :knowledge-graph through :ontology without either edge being asserted.
    cortex.approve(cortex.ingest(TTL).branch());

    String ancestors =
        cortex.query(
            """
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            SELECT ?ancestor WHERE {
              <https://example.org/vocab/shacl> skos:broaderTransitive ?ancestor .
            }
            """);
    assertThat(ancestors).contains("vocab/ontology").contains("vocab/knowledge-graph");

    String narrower =
        cortex.query(
            """
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            SELECT ?narrower WHERE {
              <https://example.org/vocab/knowledge-graph> skos:narrower ?narrower .
            }
            """);
    assertThat(narrower).as("the narrower rule inverts skos:broader").contains("vocab/ontology");
  }

  @Test
  void shouldInferSchemeMembershipFromASingleTopConceptStatement() {
    // skos:topConceptOf is a sub-property of skos:inScheme, so one asserted statement yields both
    // the inverse (hasTopConcept, by rule) and membership (inScheme, by the generic sub-property
    // rule).
    cortex.approve(cortex.ingest(TTL).branch());

    String result =
        cortex.query(
            """
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            SELECT ?c ?s WHERE {
              <https://example.org/vocab/topics> skos:hasTopConcept ?c .
              ?c skos:inScheme ?s .
            }
            """);

    assertThat(result).contains("vocab/knowledge-graph").contains("vocab/topics");
  }

  @Test
  void shouldSearchConceptsByTheirInferredPrefLabel() {
    // Cortex indexes rdfs:label, and skos:prefLabel is a sub-property of it -- which is the whole
    // reason a SKOS graph is searchable at all without mapping labels by hand.
    cortex.approve(cortex.ingest(TTL).branch());

    assertThat(cortex.search("knowledge")).contains("knowledge-graph");
  }

  @Test
  void shouldSearchConceptsByTheirInferredAltLabel() {
    cortex.approve(cortex.ingest(TTL).branch());

    assertThat(cortex.search("Constraint"))
        .as("skos:altLabel is a sub-property of rdfs:label too, so synonyms are searchable")
        .contains("shacl");
  }

  @Test
  void shouldSearchConceptsByADefinitionTurnedIntoAComment() {
    // skos:definition is a sub-property of skos:note, NOT of rdfs:comment, so without the
    // tutorial's definitionComment rule none of these definitions would be indexed or rendered.
    cortex.approve(cortex.ingest(TTL).branch());

    assertThat(cortex.search("validating")).contains("shacl");
  }

  @Test
  void shouldRejectATermOutsideTheVocabularyNamespace() {
    IngestResult result =
        cortex.ingest(
            """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .

            <https://elsewhere.example/thing> a skos:Concept ;
                skos:prefLabel "Elsewhere" ;
                skos:definition "Not in the vocabulary namespace." .
            """);

    assertThat(result.valid()).isFalse();
    assertThat(result.branch()).isNull();
    assertThat(result.errors()).contains("https://example.org/vocab/{name}");
  }

  @Test
  void shouldRejectAConceptWithNoDefinition() {
    IngestResult result =
        cortex.ingest(
            """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .

            <https://example.org/vocab/undefined> a skos:Concept ;
                skos:prefLabel "Undefined" .
            """);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("every concept needs a skos:definition");
  }

  @Test
  void shouldRejectASecondPrefLabel() {
    // SKOS's own integrity condition, and the constraint that stops an agent quietly introducing a
    // second name for a concept that already has one.
    IngestResult result =
        cortex.ingest(
            """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .

            <https://example.org/vocab/twice> a skos:Concept ;
                skos:prefLabel "Once" , "Twice" ;
                skos:definition "A concept named two ways." .
            """);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("every term needs exactly one skos:prefLabel");
  }

  @Test
  void shouldRejectAClassTheShapesDoNotAdmit() {
    // skos:Collection is declared by SKOS, so linting passes it -- only the shapes' sh:in list
    // keeps it out. The tutorial's rejection table claims exactly this split.
    IngestResult result =
        cortex.ingest(
            """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .

            <https://example.org/vocab/bundle> a skos:Collection ;
                skos:prefLabel "A bundle" .
            """);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors())
        .contains("only skos:Concept and skos:ConceptScheme may be asserted");
  }

  @Test
  void shouldRejectAPredicateSkosDoesNotDeclare() {
    // Linting, not SHACL: the closed-world vocabulary check fires before anything is staged.
    LintResult result =
        cortex.lint(
            """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            @prefix ex: <https://example.org/made-up#> .

            <https://example.org/vocab/thing> a skos:Concept ;
                ex:invented "nope" .
            """);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("invented");
  }
}
