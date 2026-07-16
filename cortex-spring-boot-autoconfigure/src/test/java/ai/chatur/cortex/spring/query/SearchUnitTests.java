package ai.chatur.cortex.spring.query;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.SearchResult;
import ai.chatur.cortex.spring.CortexConfiguration;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CortexConfiguration.class)
public class SearchUnitTests {

  @Autowired Cortex cortex;

  @Autowired QueryTools queryTools;

  static final String TTL =
"""
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

      @prefix : <example://ontology#> .
      @prefix kb: <example://kb/> .

      kb:SearchAgent a :Agent .

      kb:SearchTask a :Task ;
          :assignedTo kb:SearchAgent ;
          rdfs:label "quarterly report" .
""";

  void approve(String ttl) throws IOException {
    IngestResult ingestResult = cortex.ingest(ttl);
    assert (ingestResult.valid());
    if (ingestResult.branch() != null) {
      cortex.approve(ingestResult.branch());
    }
  }

  @Test
  void shouldFindApprovedResourcesByLabel() throws IOException {
    approve(TTL);

    String result = cortex.search("quarterly");
    assert (result != null);
    assert (result.contains("SearchTask"));
  }

  @Test
  void toolShouldSearch() throws IOException {
    approve(TTL);

    String result = queryTools.search("report");
    assert (result.contains("SearchTask"));
  }

  @Test
  void shouldNotDuplicateSearchResultsWhenInferenceIsRecomputed() throws IOException {
    approve(TTL);
    cortex.recomputeInference();
    cortex.recomputeInference();

    List<SearchResult> results = cortex.searchSubjects("quarterly");
    assert (results.stream()
            .filter(result -> result.subject().localName().equals("SearchTask"))
            .count()
        == 1);
  }

  @Test
  void shouldSearchSubjects() throws IOException {
    approve(TTL);

    List<SearchResult> results = cortex.searchSubjects("quarterly");
    assert (!results.isEmpty());
    SearchResult hit =
        results.stream()
            .filter(result -> result.subject().localName().equals("SearchTask"))
            .findFirst()
            .orElseThrow();
    assert (hit.match() != null);
  }
}
