package ai.chatur.cortex.spring.query;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.spring.CortexConfiguration;
import java.io.IOException;
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
      @prefix o: <cortex://ontology/> .
      @prefix : <cortex://assertions/> .

      :SearchAgent a o:Agent .

      :SearchTask a o:Task ;
          o:assignedTo :SearchAgent ;
          rdfs:label "quarterly report" .
""";

  @Test
  void shouldFindApprovedResourcesByLabel() throws IOException {
    IngestResult ingestResult = cortex.ingest(TTL);
    assert (ingestResult.valid());
    cortex.approve(ingestResult.branch());

    String result = cortex.search("quarterly");
    assert (result != null);
    assert (result.contains("SearchTask"));
  }

  @Test
  void toolShouldSearch() throws IOException {
    IngestResult ingestResult = cortex.ingest(TTL);
    assert (ingestResult.valid());
    cortex.approve(ingestResult.branch());

    String result = queryTools.search("report");
    assert (result.contains("SearchTask"));
  }
}
