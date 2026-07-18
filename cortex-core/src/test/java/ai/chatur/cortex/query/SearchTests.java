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

      kb:communication-api a :Task ;
          :assignedTo kb:SearchAgent ;
          rdfs:label "Communication API" ;
          rdfs:comment "Handles inbound and outbound messaging for partner systems" .

      kb:BudgetTask a :Task ;
          :assignedTo kb:SearchAgent ;
          rdfs:label "Budget planning" ;
          rdfs:comment "Feeds the quarterly report cycle" .

      kb:AlphaReport a :Task ;
          :assignedTo kb:SearchAgent ;
          rdfs:label "annual summary" .

      kb:BetaReport a :Task ;
          :assignedTo kb:SearchAgent ;
          rdfs:label "annual summary" .
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

  /**
   * Regression: query text must pass through the same analysis chain as indexed text.
   *
   * <p>Splitting the query on whitespace and appending {@code ~} forces every token down Lucene's
   * fuzzy multi-term path, which resolves terms via {@code Analyzer.normalize()} — lower-casing
   * only, no tokenization. {@code communication-api} therefore stayed one term, while the index
   * (tokenized by {@code StandardAnalyzer}'s UAX#29 tokenizer) held only {@code communication} and
   * {@code api}. At the default edit distance of 2 nothing matched, and the search silently
   * returned nothing.
   */
  @Test
  void shouldFindResourceWhenQueryIsHyphenated() {
    List<SearchResult> results = cortex.searchSubjects("communication-api");

    assertThat(results)
        .as("a hyphenated query must match the same resource as its whitespace-separated form")
        .extracting(result -> result.subject().localName())
        .contains("communication-api");
  }

  @Test
  void shouldFindResourceWhenQueryIsWhitespaceSeparated() {
    List<SearchResult> results = cortex.searchSubjects("communication api");

    assertThat(results)
        .as("the already-working path must keep working")
        .extracting(result -> result.subject().localName())
        .contains("communication-api");
  }

  /**
   * Separators that the tokenizer treats as word breaks must all reach the same resource.
   *
   * <p>{@code _} and {@code .} are deliberately absent: UAX#29 joins letters across them ({@code
   * ExtendNumLet} and {@code MidNumLet} — the rule that keeps {@code example.com} in one piece), so
   * {@code communication_api} is a single token. That is consistent rather than broken, because a
   * literal written that way indexes as a single token too; query and index analysis still agree,
   * which is the property that matters.
   */
  @Test
  void shouldFindResourceRegardlessOfSeparator() {
    for (String query : List.of("communication/api", "communication,api", "Communication API")) {
      assertThat(cortex.searchSubjects(query))
          .as("query %s must analyze to the same tokens as the indexed label", query)
          .extracting(result -> result.subject().localName())
          .contains("communication-api");
    }
  }

  @Test
  void shouldNarrowResultsAsTermsAreAdded() {
    List<SearchResult> broad = cortex.searchSubjects("communication");
    List<SearchResult> narrow = cortex.searchSubjects("communication quarterly");

    assertThat(broad).as("the single-term query matches the Communication API task").isNotEmpty();
    assertThat(narrow)
        .as("every term is required, so an unrelated extra term must not widen the results")
        .hasSizeLessThanOrEqualTo(broad.size());
    assertThat(narrow)
        .as("no resource carries both terms in one literal")
        .extracting(result -> result.subject().localName())
        .doesNotContain("communication-api");
  }

  @Test
  void shouldFindResourceByComment() {
    List<SearchResult> results = cortex.searchSubjects("messaging");

    assertThat(results)
        .as("comments are indexed in their own field and remain searchable")
        .extracting(result -> result.subject().localName())
        .contains("communication-api");
    assertThat(results.getFirst().match())
        .as("the matching literal is reported for comment hits too, not only label hits")
        .isNotNull();
  }

  @Test
  void shouldRankLabelMatchesAboveCommentMatches() {
    List<SearchResult> results = cortex.searchSubjects("quarterly");

    assertThat(results)
        .as("both the label of SearchTask and the comment of BudgetTask contain the term")
        .extracting(result -> result.subject().localName())
        .containsExactly("SearchTask", "BudgetTask");
    assertThat(results.getFirst().score())
        .as("a name match outranks a description match")
        .isGreaterThan(results.getLast().score());
  }

  @Test
  void shouldReportEachSubjectOnce() {
    List<SearchResult> results = cortex.searchSubjects("communication");

    assertThat(results)
        .as("the label and comment of one resource are separate documents but one result")
        .extracting(result -> result.subject().localName())
        .containsOnlyOnce("communication-api");
  }

  /**
   * Two resources carry the same label, so textual relevance cannot separate them; only how often
   * each has been opened can. Viewing one is what promotes it.
   */
  @Test
  void shouldRankFrequentlyViewedResourcesHigher() {
    assertThat(cortex.searchSubjects("annual summary"))
        .as("both identically labelled resources match before anything is viewed")
        .extracting(result -> result.subject().localName())
        .contains("AlphaReport", "BetaReport");

    for (int view = 0; view < 10; view++) {
      cortex.describe("example://kb/BetaReport");
    }

    List<SearchResult> ranked = cortex.searchSubjects("annual summary");

    assertThat(ranked.getFirst().subject().localName())
        .as("the repeatedly opened resource outranks its identically labelled twin")
        .isEqualTo("BetaReport");
    assertThat(ranked.getFirst().score()).isGreaterThan(ranked.getLast().score());
  }

  @Test
  void shouldNotLetPopularityOverrideTextualRelevance() {
    for (int view = 0; view < 500; view++) {
      cortex.describe("example://kb/BetaReport");
    }

    List<SearchResult> results = cortex.searchSubjects("quarterly");

    assertThat(results)
        .as("a heavily viewed resource that does not match the text must not appear at all")
        .extracting(result -> result.subject().localName())
        .doesNotContain("BetaReport");
  }

  @Test
  void shouldNotMatchUnrelatedShortTokensFuzzily() {
    List<SearchResult> results = cortex.searchSubjects("api");

    assertThat(results)
        .as("short tokens are matched exactly, so 'api' must not fuzzily reach 'a', 'apt', etc.")
        .isNotEmpty()
        .allSatisfy(
            result -> assertThat(result.subject().localName()).isEqualTo("communication-api"));
  }
}
