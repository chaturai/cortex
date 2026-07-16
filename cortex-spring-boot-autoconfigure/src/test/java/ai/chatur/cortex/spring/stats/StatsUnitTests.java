package ai.chatur.cortex.spring.stats;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.CortexStats;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.spring.CortexConfiguration;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CortexConfiguration.class)
public class StatsUnitTests {

  @Autowired Cortex cortex;

  /** Assertions never seen before, so every test stages a branch regardless of run order. */
  String freshAssertion() {
    return """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task-%1$s :assignedTo kb:Agent-%1$s .
        """
        .formatted(UUID.randomUUID());
  }

  @Test
  void shouldCountTriplesAddedTodayFromProvenanceGraph() throws IOException {
    IngestResult ingestResult = cortex.ingest(freshAssertion());
    cortex.approve(ingestResult.branch());

    assert (cortex.getStats().triplesAddedToday() >= 1);
  }

  @Test
  void pendingBranchesShouldExcludeProvenanceGraph() throws IOException {
    IngestResult approved = cortex.ingest(freshAssertion());
    cortex.approve(approved.branch());
    IngestResult staged = cortex.ingest(freshAssertion());

    CortexStats stats = cortex.getStats();
    assert (stats.pendingBranches() == cortex.listBranches().size());

    cortex.reject(staged.branch());
  }
}
