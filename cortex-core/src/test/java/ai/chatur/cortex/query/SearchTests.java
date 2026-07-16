package ai.chatur.cortex.query;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.SearchResult;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex}'s search role: full-text search over approved assertions.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class SearchTests {

  private static final String TTL =
      """
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      @prefix : <example://ontology#> .
      @prefix kb: <example://kb/> .

      kb:SearchAgent a :Agent .

      kb:SearchTask a :Task ;
          :assignedTo kb:SearchAgent ;
          rdfs:label "quarterly report" .
      """;

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
    cortex.approve(cortex.ingest(TTL).branch());
  }

  @Test
  void shouldFindApprovedResourcesByLabel() {
    String result = cortex.search("quarterly");

    assertThat(result).isNotNull().contains("SearchTask");
  }

  @Test
  void shouldSearchSubjects() {
    List<SearchResult> results = cortex.searchSubjects("quarterly");

    assertThat(results).isNotEmpty();
    SearchResult hit =
        results.stream()
            .filter(result -> result.subject().localName().equals("SearchTask"))
            .findFirst()
            .orElseThrow();
    assertThat(hit.match()).as("the matching indexed literal is reported").isNotNull();
  }
}
