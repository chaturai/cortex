package ai.chatur.cortex.spring.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.chatur.cortex.CortexQuery;
import ai.chatur.cortex.CortexSearch;
import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.SearchResult;
import ai.chatur.cortex.Term;
import java.util.List;
import org.apache.jena.query.QueryParseException;
import org.junit.jupiter.api.Test;

/**
 * Plain JUnit tests for {@link QueryTools}, against hand-rolled fakes of its two narrow Phase-3
 * role dependencies ({@link CortexQuery}, {@link CortexSearch}) rather than a Spring context.
 *
 * <p>This covers the SPARQL query-type guard in {@link QueryTools#execute}, which previously had
 * zero tests despite being the only thing standing between untrusted agent input and a
 * write-capable {@link org.apache.jena.query.Dataset}: an agent that discovered it could pass an
 * {@code INSERT}/{@code UPDATE} string to the "query"/"ask"/"describe" tools would be rejected only
 * if this guard is intact.
 */
class QueryToolsTests {

  private static final String RECORDING_QUERY_RESULT = "query-result";

  @Test
  void queryShouldDelegateWhenGivenASelectQuery() {
    FakeQuery query = new FakeQuery(RECORDING_QUERY_RESULT);
    QueryTools tools = new QueryTools(query, new FakeSearch());

    String result = tools.query("SELECT * WHERE { ?s ?p ?o }");

    assertThat(result).isEqualTo(RECORDING_QUERY_RESULT);
    assertThat(query.lastSparql).isEqualTo("SELECT * WHERE { ?s ?p ?o }");
  }

  @Test
  void queryShouldRejectAnAskQuery() {
    QueryTools tools = new QueryTools(new FakeQuery(RECORDING_QUERY_RESULT), new FakeSearch());

    assertThatThrownBy(() -> tools.query("ASK WHERE { ?s ?p ?o }"))
        .as(
            "the query tool only accepts SPARQL SELECT, so an ASK query is rejected before reaching CortexQuery")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SELECT");
  }

  @Test
  void askShouldRejectASelectQuery() {
    QueryTools tools = new QueryTools(new FakeQuery(RECORDING_QUERY_RESULT), new FakeSearch());

    assertThatThrownBy(() -> tools.ask("SELECT * WHERE { ?s ?p ?o }"))
        .as("the ask tool only accepts SPARQL ASK")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ASK");
  }

  @Test
  void describeShouldRejectASelectQuery() {
    QueryTools tools = new QueryTools(new FakeQuery(RECORDING_QUERY_RESULT), new FakeSearch());

    assertThatThrownBy(() -> tools.describe("SELECT * WHERE { ?s ?p ?o }"))
        .as("the describe tool only accepts SPARQL DESCRIBE")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DESCRIBE");
  }

  @Test
  void executeShouldRejectMalformedSparqlBeforeReachingCortexQuery() {
    FakeQuery query = new FakeQuery(RECORDING_QUERY_RESULT);
    QueryTools tools = new QueryTools(query, new FakeSearch());

    assertThatThrownBy(() -> tools.query("this is not sparql"))
        .as("malformed SPARQL is rejected by QueryFactory before CortexQuery is ever called")
        .isInstanceOf(QueryParseException.class);
    assertThat(query.lastSparql).as("CortexQuery is never reached for unparseable input").isNull();
  }

  @Test
  void searchShouldDelegateToCortexSearch() {
    FakeSearch search = new FakeSearch();
    QueryTools tools = new QueryTools(new FakeQuery(RECORDING_QUERY_RESULT), search);

    String result = tools.search("quarterly");

    assertThat(result).isEqualTo(FakeSearch.RESULT);
    assertThat(search.lastText).isEqualTo("quarterly");
  }

  /** Hand-rolled fake of {@link CortexQuery}, recording the last SPARQL string it was given. */
  private static final class FakeQuery implements CortexQuery {
    private final String result;
    private String lastSparql;

    FakeQuery(String result) {
      this.result = result;
    }

    @Override
    public List<Term> getInstances(String type) {
      throw new UnsupportedOperationException("not exercised by QueryTools");
    }

    @Override
    public List<ProvenancedStatement> describe(String id) {
      throw new UnsupportedOperationException("not exercised by QueryTools");
    }

    @Override
    public String query(String sparql) {
      this.lastSparql = sparql;
      return result;
    }
  }

  /** Hand-rolled fake of {@link CortexSearch}, recording the last search text it was given. */
  private static final class FakeSearch implements CortexSearch {
    static final String RESULT = "search-result";
    private String lastText;

    @Override
    public String search(String text) {
      this.lastText = text;
      return RESULT;
    }

    @Override
    public List<SearchResult> searchSubjects(String text) {
      throw new UnsupportedOperationException("not exercised by QueryTools");
    }
  }
}
