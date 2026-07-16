package ai.chatur.cortex.branch;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchInfo;
import ai.chatur.cortex.BranchRename;
import ai.chatur.cortex.BranchStatement;
import ai.chatur.cortex.BranchSubject;
import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex}'s branch review surface: listing, editing, renaming, and
 * reading branches pending review, including the {@code "provenance"}-is-never-a-branch invariant
 * and the reader/mutator behavior for an unknown branch name.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class BranchTests {

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void shouldUpdateBranchWithDeletionsAndEdits() {
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:UpdateTask :assignedTo kb:FirstAgent ;
            :assignedTo kb:DroppedAgent .
        """;
    IngestResult ingestResult = cortex.ingest(ttl);
    String branch = ingestResult.branch();

    List<BranchSubject> subjects = cortex.getBranchSubjects(branch);
    assertThat(subjects).hasSize(1);
    BranchSubject subject = subjects.getFirst();
    BranchStatement dropped =
        subject.statements().stream()
            .filter(statement -> statement.object().contains("DroppedAgent"))
            .findFirst()
            .orElseThrow();
    BranchStatement edited =
        subject.statements().stream()
            .filter(statement -> statement.object().contains("FirstAgent"))
            .findFirst()
            .orElseThrow();

    boolean updated =
        cortex.updateBranch(
            branch,
            List.of(
                new BranchChange(
                    subject.uri(),
                    dropped.predicateUri(),
                    dropped.object(),
                    dropped.literal(),
                    dropped.datatype(),
                    null),
                new BranchChange(
                    subject.uri(),
                    edited.predicateUri(),
                    edited.object(),
                    edited.literal(),
                    edited.datatype(),
                    "example://kb/EditedAgent")));
    assertThat(updated).as("the branch existed, so the edits were applied").isTrue();

    List<BranchStatement> statements = cortex.getBranchSubjects(branch).getFirst().statements();
    assertThat(statements).as("the dropped statement is gone, only the edit remains").hasSize(1);
    assertThat(statements.getFirst().object())
        .as("the surviving statement's object was rewritten")
        .isEqualTo("example://kb/EditedAgent");
  }

  @Test
  void shouldRenameBranchSubjects() {
    IngestResult ingestResult =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:RenameTask :assignedTo kb:RenameAgent .
            """);
    String branch = ingestResult.branch();

    BranchSubject task = cortex.getBranchSubjects(branch).getFirst();
    String agent = task.statements().getFirst().object();

    boolean renamedAgent =
        cortex.renameBranchSubjects(
            branch, List.of(new BranchRename(agent, "example://kb/RenamedAgent")));
    assertThat(renamedAgent).isTrue();
    List<BranchSubject> subjects = cortex.getBranchSubjects(branch);
    assertThat(subjects).hasSize(1);
    assertThat(subjects.getFirst().uri()).isEqualTo(task.uri());
    assertThat(subjects.getFirst().statements().getFirst().object())
        .as("the statement referencing the renamed agent as object was rewritten")
        .isEqualTo("example://kb/RenamedAgent");

    // Renaming the task itself removes the statements describing it as subject, rather than
    // rewriting them.
    boolean renamedTask =
        cortex.renameBranchSubjects(
            branch, List.of(new BranchRename(task.uri(), "example://kb/RenamedTask")));
    assertThat(renamedTask).isTrue();
    assertThat(cortex.getBranchSubjects(branch))
        .as("renaming a subject removes its own statements rather than rewriting them")
        .isEmpty();
  }

  @Test
  void shouldRemoveReferencesToRemovedSubjects() {
    String ttl =
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:DanglingTask :assignedTo kb:DanglingAgent .
        kb:DanglingAgent rdfs:label "the agent" .
        """;
    IngestResult ingestResult = cortex.ingest(ttl);
    String branch = ingestResult.branch();

    BranchSubject agent =
        cortex.getBranchSubjects(branch).stream()
            .filter(subject -> subject.name().startsWith("DanglingAgent"))
            .findFirst()
            .orElseThrow();
    BranchStatement label = agent.statements().getFirst();
    boolean updated =
        cortex.updateBranch(
            branch,
            List.of(
                new BranchChange(
                    agent.uri(),
                    label.predicateUri(),
                    label.object(),
                    label.literal(),
                    label.datatype(),
                    null)));
    assertThat(updated).isTrue();

    assertThat(cortex.getBranchSubjects(branch))
        .as(
            "removing the agent's last statement leaves it with none, so the statement"
                + " referencing it as object (assignedTo) is removed too — no dangling reference"
                + " survives")
        .isEmpty();
  }

  @Test
  void listBranchesShouldExcludeProvenanceGraph() {
    IngestResult ingestResult = cortex.ingest(sampleTtl());
    cortex.approve(ingestResult.branch());

    assertThat(cortex.listBranches())
        .as("the provenance graph is not a branch pending review")
        .doesNotContain("provenance");
    assertThat(cortex.hasBranch("provenance")).isFalse();
  }

  @Test
  void updateBranch_unknownBranch_returnsFalse() {
    boolean updated = cortex.updateBranch("does-not-exist", List.of());

    assertThat(updated)
        .as(
            "updateBranch is a mutator, so its \"no such branch\" signal is a boolean false,"
                + " unlike the readers below which return a neutral empty result")
        .isFalse();
  }

  @Test
  void getBranch_unknownBranch_returnsEmptyWithNoError() {
    // Jena returns an empty Model (no statements) for a missing named graph, but that empty Model
    // still carries the dataset's shared prefix mapping, so serializing it still emits the full
    // prefix header block with zero data triples. "No error, and nothing branch-specific comes
    // back" is the accurate invariant to pin, not literal emptiness.
    String ttl = cortex.getBranch("does-not-exist");

    assertThat(ttl)
        .as("an unknown branch raises no error; only the shared prefix header comes back, no data")
        .containsPattern("PREFIX\\s+:\\s+<example://ontology#>")
        .containsPattern("PREFIX\\s+kb:\\s+<example://kb/>");
    assertThat(ttl)
        .as("no data triples leak through for an unknown branch")
        .doesNotContain("assignedTo");
  }

  @Test
  void getBranch_provenance_returnsEmptyLikeUnknownBranch() {
    // Guarantee the provenance graph is non-empty, so the point of the test is that it stays
    // hidden even so.
    cortex.approve(cortex.ingest(sampleTtl()).branch());

    String result = cortex.getBranch("provenance");

    assertThat(result)
        .as(
            "getBranch(\"provenance\") is guarded the same way the write paths are, so the real"
                + " provenance graph — reification triples included — never renders as a branch")
        .doesNotContain("wasGeneratedBy");
  }

  @Test
  void getBranchInfo_provenance_returnsEmptyLikeUnknownBranch() {
    cortex.approve(cortex.ingest(sampleTtl()).branch());

    BranchInfo info = cortex.getBranchInfo("provenance");

    assertThat(info.name()).isEqualTo("provenance");
    assertThat(info.size())
        .as(
            "getBranchInfo(\"provenance\") does not report the size of the entire provenance"
                + " graph; the guard's missing value reports 0, as for a branch never staged")
        .isZero();
    assertThat(info.started())
        .as("the guard's missing value reports no start time, as for an unknown branch")
        .isNull();
  }

  @Test
  void getBranchSubjects_provenance_returnsEmptyLikeUnknownBranch() {
    cortex.approve(cortex.ingest(sampleTtl()).branch());

    List<BranchSubject> subjects = cortex.getBranchSubjects("provenance");

    assertThat(subjects)
        .as(
            "getBranchSubjects(\"provenance\") does not return the reifiers and activities of the"
                + " real provenance graph; the guard's missing value is an empty list")
        .isEmpty();
  }

  @Test
  void getBranch_returnsRiotStyleTurtleWithUnabbreviatedProvenanceUris() {
    IngestResult ingestResult = cortex.ingest(sampleTtl());
    String branch = ingestResult.branch();

    String ttl = cortex.getBranch(branch);

    // Prefix declarations are emitted SPARQL-style — `PREFIX name: <uri>`, column-aligned, with no
    // leading `@` and no trailing `.` — rather than Turtle's own `@prefix name: <uri> .` form; this
    // is RIOT's Turtle output (ai.chatur.cortex.core.jena.Rdf).
    assertThat(ttl)
        .as("RIOT emits SPARQL-style PREFIX lines for the ontology namespace")
        .containsPattern("PREFIX\\s+:\\s+<example://ontology#>");
    assertThat(ttl)
        .as("RIOT emits SPARQL-style PREFIX lines for the kb namespace")
        .containsPattern("PREFIX\\s+kb:\\s+<example://kb/>");
    assertThat(ttl).as("Turtle's @prefix form is not used").doesNotContain("@prefix");
    // No "prov" prefix is registered anywhere, so the branch's own provenance activity — a
    // prov:Activity carrying rdfs:label, rdfs:comment, prov:startedAtTime — renders with that
    // predicate and type spelled out as full angle-bracketed URIs, not abbreviated.
    assertThat(ttl)
        .as("the branch's provenance activity type is rendered as a full URI, unabbreviated")
        .contains("<http://www.w3.org/ns/prov#Activity>");
    assertThat(ttl).as("the branch carries the staged data triple").contains("assignedTo");
    // Likewise "cortex" has no registered prefix, so the branch's own activity resource renders as
    // a full bracketed URI rather than an abbreviated "cortex:branch-..." form.
    assertThat(ttl)
        .as("the branch name is embedded as the activity's own resource, as a full URI")
        .contains("<cortex://" + branch + ">");
  }

  private static String sampleTtl() {
    return """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:BranchFixtureTask :assignedTo kb:BranchFixtureAgent .
        """;
  }
}
