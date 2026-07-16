package ai.chatur.cortex.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.support.CortexFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex}'s archive role: exporting/restoring the whole dataset for
 * backup, and the exclusion of provenance triples from the approved-assertions serialization.
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
  void shouldRestoreAssertionsFromExport() {
    String approvedTtl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:BackupTask :assignedTo kb:BackupAgent .
        """;
    cortex.approve(cortex.ingest(approvedTtl).branch());
    IngestResult staged =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:StagedTask :assignedTo kb:StagedAgent .
            """);

    String backup = cortex.exportAssertions();
    assertThat(backup).as("the backup carries the approved assertions").contains("assignedTo");

    cortex.reject(staged.branch());
    cortex.importAssertions(backup);

    assertThat(cortex.getAssertions())
        .as("the approved assertions were restored from the backup")
        .contains("assignedTo");
    assertThat(cortex.hasBranch(staged.branch()))
        .as("the staged branch, discarded before the restore, was restored from the backup too")
        .isTrue();
  }

  @Test
  void assertionsShouldNotCarryProvenance() {
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:ProvenanceTask :assignedTo kb:ProvenanceAgent .
                """)
            .branch());

    String trig = cortex.getAssertions();

    assertThat(trig).as("the approved data triple is present").contains("assignedTo");
    assertThat(trig)
        .as("reification triples are excluded from the assertions graph")
        .doesNotContain("reifies");
    assertThat(trig)
        .as("provenance activity triples are excluded from the assertions graph")
        .doesNotContain("wasGeneratedBy");
    assertThat(trig)
        .as("provenance timing triples are excluded from the assertions graph")
        .doesNotContain("endedAtTime");
  }

  @Test
  void importAssertionsShouldLeaveTheDatasetUntouchedWhenTheBackupCannotBeParsed() {
    // CortexArchive.importAssertions's Javadoc promises: "If the backup cannot be parsed, the
    // dataset is left untouched." The implementation clears the dataset graph *before* parsing,
    // inside a single write transaction, so the promise holds only if Jena aborts (and rolls back)
    // the whole transaction when the parse throws. This pins that behavior down with a real,
    // unparsable payload rather than trusting the implementation comment.
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:SurvivingTask :assignedTo kb:SurvivingAgent .
                """)
            .branch());
    IngestResult stagedBranch =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:SurvivingStagedTask :assignedTo kb:SurvivingStagedAgent .
            """);
    String beforeAssertions = cortex.getAssertions();
    String beforeBranch = cortex.getBranch(stagedBranch.branch());

    assertThatThrownBy(() -> cortex.importAssertions("this is not valid { TriG at all"))
        .as(
            "a backup that cannot be parsed must throw rather than be silently accepted "
                + "as an empty dataset")
        .isInstanceOf(RuntimeException.class);

    assertThat(cortex.getAssertions())
        .as(
            "Javadoc promise: \"If the backup cannot be parsed, the dataset is left untouched\" — "
                + "the approved assertions must survive a failed import byte-for-byte")
        .isEqualTo(beforeAssertions);
    assertThat(cortex.hasBranch(stagedBranch.branch()))
        .as("the staged branch, pending before the failed import, must still be pending after")
        .isTrue();
    assertThat(cortex.getBranch(stagedBranch.branch()))
        .as("the staged branch's content must survive a failed import byte-for-byte too")
        .isEqualTo(beforeBranch);
  }
}
