package ai.chatur.cortex.inference;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.SearchResult;
import ai.chatur.cortex.Term;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex}'s inference role: the incremental extension of the
 * inference closure on {@code approve}, and idempotence of an explicit {@link
 * Cortex#recomputeInference()}.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class InferenceTests {

  private static final String TTL =
      """
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      @prefix : <example://ontology#> .
      @prefix kb: <example://kb/> .

      kb:InferenceAgent a :Agent .

      kb:InferenceTask a :Task ;
          :assignedTo kb:InferenceAgent ;
          rdfs:label "quarterly inference report" .
      """;

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void approvedTriplesShouldExtendInferenceClosureIncrementally() {
    String firstTaskUri = "example://kb/FirstTask";
    String secondTaskUri = "example://kb/SecondTask";
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:FirstTask :assignedTo kb:FirstAgent .
                """)
            .branch());
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:SecondTask :assignedTo kb:SecondAgent .
                """)
            .branch());

    // Both tasks are typed by the domain rule (rdfs:domain assignedTo -> :Task), without any
    // explicit recomputation: approve extends the inference closure incrementally.
    List<Term> instances = cortex.getInstances("example://ontology#Task");
    assertThat(instances).contains(new Term("kb", "FirstTask", firstTaskUri));
    assertThat(instances).contains(new Term("kb", "SecondTask", secondTaskUri));
  }

  @Test
  void recomputingInferenceShouldNotDuplicateSearchResults() {
    cortex.approve(cortex.ingest(TTL).branch());

    cortex.recomputeInference();
    cortex.recomputeInference();

    List<SearchResult> results = cortex.searchSubjects("quarterly");
    assertThat(
            results.stream()
                .filter(result -> result.subject().localName().equals("InferenceTask"))
                .count())
        .as("recomputing inference twice does not duplicate the search index entry")
        .isEqualTo(1);
  }
}
