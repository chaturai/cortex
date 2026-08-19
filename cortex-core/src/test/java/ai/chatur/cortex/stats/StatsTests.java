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
  void inferredTriplesShouldCountOnlyWhatTheReasonerDerived() {
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:InferredStatsTask :assignedTo kb:InferredStatsAgent .
                """)
            .branch());

    CortexStats stats = cortex.getStats();

    assertThat(stats.assertionTriples()).as("the one approved triple").isEqualTo(1);
    assertThat(stats.inferredTriples())
        .as(
            "the domain and range rules each derive an rdf:type; the ontology's own axioms are the"
                + " reasoner's schema, not its conclusions, and are not counted")
        .isEqualTo(2);
  }

  @Test
  void inferredTriplesShouldBeZeroWhenNothingHasBeenApproved() {
    assertThat(cortex.getStats().inferredTriples())
        .as("an empty graph has nothing to infer over, so the ontology's axioms cannot inflate it")
        .isZero();
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

  @Test
  void pendingBranchesShouldExcludeUsageGraph() {
    IngestResult approved =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:ViewedStatsTask :assignedTo kb:ViewedStatsAgent .
            """);
    cortex.approve(approved.branch());

    // opening a resource records a view; once a batch flushes it materializes the reserved
    // cortex://usage named graph, which is not a branch and must not inflate the count
    for (int view = 0; view < 30; view++) {
      cortex.describe("example://kb/ViewedStatsTask");
    }

    assertThat(cortex.getStats().pendingBranches())
        .as("the cortex://usage graph a view creates is not a pending branch")
        .isEqualTo(cortex.listBranches().size())
        .isZero();
  }
}
