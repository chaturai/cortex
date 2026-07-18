package ai.chatur.cortex.spring.query;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.CortexSearch;
import ai.chatur.cortex.SearchResult;
import ai.chatur.cortex.Term;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/**
 * Plain JUnit tests for {@link SearchController}, against a hand-rolled fake of its single narrow
 * Phase-3 role dependency ({@link CortexSearch}) rather than a Spring context. Previously untested
 * — {@code SearchUnitTests} exercised {@code Cortex.search}/{@code searchSubjects} directly and
 * {@code QueryTools}, never this controller.
 */
class SearchControllerTests {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void searchShouldReturnNoResultsForNullOrBlankQuery(String q) {
    SearchController controller = new SearchController(new FakeSearch(List.of()));
    Model model = new ExtendedModelMap();

    String view = controller.search(q, model);

    assertThat(view).isEqualTo("search");
    assertThat(model.getAttribute("q"))
        .as("a null query renders as an empty string")
        .isEqualTo(q == null ? "" : q);
    assertThat(model.getAttribute("results"))
        .as("a blank query never reaches CortexSearch")
        .isEqualTo(List.of());
  }

  @Test
  void searchShouldDelegateToSearchSubjectsWhenQueryIsPresent() {
    SearchResult hit =
        new SearchResult(
            new Term("kb", "SearchTask", "example://kb/SearchTask"), "quarterly report", 1.5);
    SearchController controller = new SearchController(new FakeSearch(List.of(hit)));
    Model model = new ExtendedModelMap();

    String view = controller.search("quarterly", model);

    assertThat(view).isEqualTo("search");
    assertThat(model.getAttribute("q")).isEqualTo("quarterly");
    assertThat(model.getAttribute("results")).isEqualTo(List.of(hit));
  }

  /** Hand-rolled fake of {@link CortexSearch}. */
  private record FakeSearch(List<SearchResult> results) implements CortexSearch {
    @Override
    public String search(String text) {
      throw new UnsupportedOperationException("SearchController only calls searchSubjects");
    }

    @Override
    public List<SearchResult> searchSubjects(String text) {
      return results;
    }
  }
}
