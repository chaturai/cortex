package ai.chatur.cortex.spring.example;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.CortexBuilder;
import ai.chatur.cortex.IngestResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Exercises the real {@code ontology.ttl}/{@code ontology.shapes}/{@code ontology.rules} shipped in
 * this module's {@code src/main/resources} directly through {@link CortexBuilder}, bypassing Spring
 * entirely.
 *
 * <p>These are the closed-world guarantees the README sells hardest — assertion IRIs must look like
 * {@code example://kb/{name}}, and every assertion needs an {@code rdfs:label} and {@code
 * rdfs:comment} — but the autoconfigure module's test fixtures (see {@code cortex-core}'s {@code
 * CortexFixtures}, mirrored by {@code
 * cortex-spring-boot-autoconfigure/src/test/resources/ontology.shapes}) use a deliberately smaller
 * Task/Agent shape with no such constraints, so before this class the constraints were exercised
 * nowhere: the example application only had {@code contextLoads()}. This class asserts against the
 * example's own shapes without weakening them.
 */
class ExampleShapesTests {

  private static Cortex cortex;

  @BeforeAll
  static void setUp() throws IOException {
    cortex =
        CortexBuilder.create()
            .ontologies(List.of(resource("ontology.ttl")))
            .shapes(List.of(resource("ontology.shapes")))
            .rules(List.of(resource("ontology.rules")))
            .build();
  }

  private static String resource(String name) throws IOException {
    return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void shouldStageAFullyConformingRule() {
    IngestResult result =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            kb:Rule1 a :Rule ;
                rdfs:label "Rule One" ;
                rdfs:comment "A complete rule." ;
                :activatedBy kb:Cond1 ;
                :hasAction kb:Act1 .

            kb:Cond1 a :Condition ;
                rdfs:label "Cond One" ;
                rdfs:comment "A complete condition." ;
                :hasExpression kb:Expr1 .

            kb:Expr1 a :Expression ;
                rdfs:label "Expr One" ;
                rdfs:comment "A complete expression." ;
                :hasCoordinate kb:Coord1 .

            kb:Coord1 a :Coordinate ;
                rdfs:label "Coord One" ;
                rdfs:comment "A complete coordinate." ;
                :hasDimension kb:Dim1 ;
                :hasValue "5" .

            kb:Dim1 a :Dimension ;
                rdfs:label "Dim One" ;
                rdfs:comment "A complete dimension." .

            kb:Act1 a :Action ;
                rdfs:label "Act One" ;
                rdfs:comment "A complete action." .
            """);

    assertThat(result.valid())
        .as("a fully-formed instance graph satisfying every closed shape and mandatory property")
        .isTrue();
    assertThat(result.branch()).isNotNull();
    assertThat(result.errors()).isNull();
  }

  @Test
  void shouldRejectAnAssertionIriWithASubPath() {
    // s:AssertionShape enforces sh:nodeKind sh:IRI and sh:pattern "^example://kb/[^/?#]+$" on every
    // subject targeted via rdf:type/hasCondition/activatedBy/.../hasValue — this IRI has a nested
    // path segment ("bad/action"), violating the pattern.
    IngestResult result =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            <example://kb/bad/action> a :Action ;
                rdfs:label "Bad Action" ;
                rdfs:comment "An action whose IRI has a sub-path." .
            """);

    assertThat(result.valid())
        .as("an assertion IRI with a nested path segment violates the example://kb/{name} pattern")
        .isFalse();
    assertThat(result.branch()).isNull();
    assertThat(result.errors())
        .as("the SHACL violation names the IRI-pattern rule")
        .contains("example://kb/{name}");
  }

  @Test
  void shouldRejectAnAssertionMissingAMandatoryLabel() {
    // s:AssertionShape requires sh:minCount 1 on rdfs:label for every targeted subject; this
    // instance has a comment but no label.
    IngestResult result =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            kb:NoLabelAction a :Action ;
                rdfs:comment "Has a comment but no label." .
            """);

    assertThat(result.valid())
        .as("s:AssertionShape requires every assertion to carry an rdfs:label")
        .isFalse();
    assertThat(result.branch()).isNull();
    assertThat(result.errors())
        .as("the SHACL violation names the missing-label rule")
        .contains("every assertion must have an rdfs:label");
  }

  @Test
  void shouldRejectAnAssertionMissingAMandatoryComment() {
    // Symmetric to the label case: s:AssertionShape also requires sh:minCount 1 on rdfs:comment.
    IngestResult result =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            kb:NoCommentAction a :Action ;
                rdfs:label "Has a label but no comment." .
            """);

    assertThat(result.valid())
        .as("s:AssertionShape requires every assertion to carry an rdfs:comment")
        .isFalse();
    assertThat(result.branch()).isNull();
    assertThat(result.errors())
        .as("the SHACL violation names the missing-comment rule")
        .contains("every assertion must have an rdfs:comment");
  }
}
