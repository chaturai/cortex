package ai.chatur.cortex.archive;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.support.CortexFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex}'s archive role: exporting the approved assertions, and the
 * exclusion of everything that is not one — staged branches and provenance alike.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class ArchiveTests {

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void exportShouldCarryTheApprovedAssertionsAsTurtle() {
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:ExportedTask :assignedTo kb:ExportedAgent .
                """)
            .branch());

    String ttl = cortex.exportAssertions();

    assertThat(ttl).as("the approved data triple is present").contains("assignedTo");
    assertThat(ttl)
        .as(
            "the store's default model is prefix-seeded from the ontology, so terms come out"
                + " abbreviated rather than as full IRIs")
        .contains("kb:ExportedTask")
        .contains("PREFIX kb:");
    assertThat(ttl)
        .as("Turtle is a single-graph syntax: no named-graph blocks, unlike the TriG this replaced")
        .doesNotContain("GRAPH");
  }

  @Test
  void exportShouldNotCarryStagedBranches() {
    cortex.ingest(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:StagedTask :assignedTo kb:StagedAgent .
        """);

    String ttl = cortex.exportAssertions();

    assertThat(ttl)
        .as(
            "the export reads the default graph, so assertions still awaiting review must not leak"
                + " into it")
        .doesNotContain("StagedTask");
  }

  @Test
  void exportShouldNotCarryProvenance() {
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:ProvenanceTask :assignedTo kb:ProvenanceAgent .
                """)
            .branch());

    String ttl = cortex.exportAssertions();

    assertThat(ttl).as("the approved data triple is present").contains("assignedTo");
    assertThat(ttl)
        .as("reification triples are excluded from the assertions graph")
        .doesNotContain("reifies");
    assertThat(ttl)
        .as("provenance activity triples are excluded from the assertions graph")
        .doesNotContain("wasGeneratedBy");
    assertThat(ttl)
        .as("provenance timing triples are excluded from the assertions graph")
        .doesNotContain("endedAtTime");
  }
}
