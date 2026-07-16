package ai.chatur.cortex.lint;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.LintResult;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Core behavior tests for {@link Cortex#lint(String)}.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class LintTests {

  private static final String VALID_TTL =
      """
      @prefix : <example://ontology#> .
      @prefix kb: <example://kb/> .

      kb:ValidTask :assignedTo kb:ValidAgent .
      """;

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void shouldReturnValidatedTtl() {
    LintResult lintResult = cortex.lint(VALID_TTL);

    assertThat(lintResult.valid()).isTrue();
    assertThat(lintResult.ttl()).isNotNull().contains("assignedTo");
    assertThat(lintResult.errors()).isNull();
  }

  @Test
  void validatedTtlShouldBeIngestible() {
    LintResult lintResult = cortex.lint(VALID_TTL);

    IngestResult ingestResult = cortex.ingest(lintResult.ttl());

    assertThat(ingestResult.valid())
        .as("output re-serialized by lint is itself valid Turtle")
        .isTrue();
  }

  @Test
  void shouldAllowTypeLabelAndCommentBeyondOntology() {
    String ttl =
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:LintTask a :Task ;
            rdfs:label "Lint task" ;
            rdfs:comment "A task used to test linting" .
        """;

    LintResult lintResult = cortex.lint(ttl);

    assertThat(lintResult.valid())
        .as("rdf:type, rdfs:label, and rdfs:comment are allowed beyond the declared ontology terms")
        .isTrue();
    assertThat(lintResult.errors()).isNull();
  }

  static Stream<Arguments> invalidTurtle() {
    return Stream.of(
        Arguments.of(
            "property not declared in the ontology",
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:LintTask :unknownProperty kb:LintAgent .
            """,
            "unknownProperty"),
        Arguments.of(
            "class not declared in the ontology",
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:LintNode a :InvalidClass .
            """,
            "InvalidClass"),
        Arguments.of("malformed Turtle syntax", "this is not turtle", null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidTurtle")
  void shouldRejectInvalidTurtle(String scenario, String ttl, String expectedErrorSubstring) {
    LintResult lintResult = cortex.lint(ttl);

    assertThat(lintResult.valid()).as(scenario + " fails lint").isFalse();
    assertThat(lintResult.ttl()).as("no validated Turtle is returned for invalid input").isNull();
    assertThat(lintResult.errors()).as("the violation is reported").isNotNull();
    if (expectedErrorSubstring != null) {
      assertThat(lintResult.errors())
          .as("the error names the offending term")
          .contains(expectedErrorSubstring);
    }
  }
}
