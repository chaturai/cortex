package ai.chatur.cortex.stats;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.CortexStats;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.support.CortexFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex#getStats()}.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class StatsTests {

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void shouldCountTriplesAddedTodayFromProvenanceGraph() {
    IngestResult ingestResult =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:StatsTask :assignedTo kb:StatsAgent .
            """);
    cortex.approve(ingestResult.branch());

    assertThat(cortex.getStats().triplesAddedToday())
        .as("today's approved triple is counted from the provenance graph")
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void pendingBranchesShouldExcludeProvenanceGraph() {
    IngestResult approved =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:ApprovedStatsTask :assignedTo kb:ApprovedStatsAgent .
            """);
    cortex.approve(approved.branch());
    IngestResult staged =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:PendingStatsTask :assignedTo kb:PendingStatsAgent .
            """);

    CortexStats stats = cortex.getStats();

    assertThat(stats.pendingBranches())
        .as(
            "pendingBranches counts exactly the branches listBranches reports, excluding provenance")
        .isEqualTo(cortex.listBranches().size());

    cortex.reject(staged.branch());
  }
}
