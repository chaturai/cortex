package ai.chatur.cortex.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.Term;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex#ingest(String)}: lint/shape gating, novelty trimming
 * against already-approved assertions, and the ingest-then-approve happy path.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}),
 * so assertions use small, stable, human-readable IRIs — no UUID suffixing is needed to dodge
 * collisions with other tests, unlike the Spring-context era this class replaces.
 */
class IngestTests {

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void shouldStageValidAssertions() {
    IngestResult ingestResult =
        cortex.ingest(
            """
            @prefix : <example://ontology#> .
            @prefix kb: <example://kb/> .

            kb:Task :assignedTo kb:Agent .
            """);

    assertThat(ingestResult.valid())
        .as("well-formed, ontology-conformant Turtle lints clean")
        .isTrue();
    assertThat(ingestResult.branch()).as("novel assertions are staged on a new branch").isNotNull();
    assertThat(cortex.hasBranch(ingestResult.branch())).isTrue();
    assertThat(ingestResult.errors()).isNull();
  }

  @Test
  void shouldNotStageAssertionsFailingShapeValidation() {
    // kb:InvalidTask is typed :Task but never assigned to an :Agent, violating TaskShape's
    // sh:minCount 1 on :assignedTo.
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:InvalidTask a :Task .
        """;

    IngestResult ingestResult = cortex.ingest(ttl);

    assertThat(ingestResult.valid()).as("a task with no assignedTo fails TaskShape").isFalse();
    assertThat(ingestResult.branch()).as("invalid assertions are never staged").isNull();
    assertThat(ingestResult.errors()).as("the SHACL violation is reported").isNotNull();
  }

  @Test
  void shouldNotStageAssertionsFailingLint() {
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:LintTask :unknownProperty kb:LintAgent .
        """;

    IngestResult ingestResult = cortex.ingest(ttl);

    assertThat(ingestResult.valid()).as("a property outside the ontology fails lint").isFalse();
    assertThat(ingestResult.branch()).isNull();
    assertThat(ingestResult.errors())
        .as("the lint violation names the offending property")
        .contains("unknownProperty");
  }

  @Test
  void shouldNotStageApprovedTriplesAgain() {
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:RepeatTask :assignedTo kb:RepeatAgent .
        """;
    IngestResult first = cortex.ingest(ttl);
    cortex.approve(first.branch());

    IngestResult second = cortex.ingest(ttl);

    assertThat(second.valid())
        .as("re-ingesting already-approved triples still lints and validates")
        .isTrue();
    assertThat(second.branch())
        .as("nothing novel remains to stage, so no branch is created")
        .isNull();
    assertThat(second.errors()).isNull();
  }

  @Test
  void shouldStageOnlyNovelTriples() {
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:PartialTask :assignedTo kb:PartialAgent .
        """;
    IngestResult approved = cortex.ingest(ttl);
    cortex.approve(approved.branch());

    IngestResult ingestResult =
        cortex.ingest(ttl + "\nkb:PartialTask :assignedTo kb:SecondAgent .");

    assertThat(ingestResult.valid()).isTrue();
    assertThat(cortex.hasBranch(ingestResult.branch())).isTrue();
    String staged = cortex.getBranch(ingestResult.branch());
    assertThat(staged).as("the new triple is staged").contains("SecondAgent");
    assertThat(staged)
        .as("the already-approved triple is trimmed, not re-staged")
        .doesNotContain("PartialAgent");
  }

  @Test
  void shouldValidateAgainstApprovedAssertions() {
    // The union of approved + incoming must conform: :UnionTask is already an :Agent-assigned
    // :Task from the approved graph, so an incoming update that only adds an rdfs:label to it
    // (without repeating :assignedTo) must still validate against the union, not the update alone.
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:UnionAgent a :Agent .

        kb:UnionTask a :Task ;
            :assignedTo kb:UnionAgent .
        """;
    cortex.approve(cortex.ingest(ttl).branch());

    String update =
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:UnionTask a :Task ;
            rdfs:label "union task" .
        """;
    IngestResult ingestResult = cortex.ingest(update);

    assertThat(ingestResult.valid())
        .as("the update validates against the union of approved and incoming assertions")
        .isTrue();
    assertThat(cortex.hasBranch(ingestResult.branch())).isTrue();
    String staged = cortex.getBranch(ingestResult.branch());
    assertThat(staged).contains("union task");
    assertThat(staged)
        .as("the already-approved assignedTo triple is not re-staged")
        .doesNotContain("assignedTo");
  }

  @Test
  void ingestApproveHappyPath() {
    String taskUri = "example://kb/HappyTask";
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:HappyTask :assignedTo kb:HappyAgent .
        """;

    IngestResult ingestResult = cortex.ingest(ttl);
    assertThat(ingestResult.valid())
        .as("well-formed, lint- and shape-conformant Turtle ingests cleanly")
        .isTrue();
    String branch = ingestResult.branch();
    assertThat(branch).as("novel assertions are staged on a new branch").isNotNull();
    assertThat(ingestResult.errors()).isNull();
    assertThat(cortex.listBranches()).as("the staged branch is pending review").contains(branch);
    assertThat(cortex.hasBranch(branch)).isTrue();

    cortex.approve(branch);

    assertThat(cortex.listBranches())
        .as("approving a branch removes it from the pending list")
        .doesNotContain(branch);
    assertThat(cortex.hasBranch(branch)).isFalse();

    var statements = cortex.describe(taskUri);
    var assignedTo =
        statements.stream()
            .filter(s -> "example://ontology#assignedTo".equals(s.predicate().uri()))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("approved triple should be queryable via describe"));
    assertThat(assignedTo.created())
        .as("the approved statement's provenance activity timestamp was recorded")
        .isNotNull();

    List<Term> tasks = cortex.getInstances("example://ontology#Task");
    assertThat(tasks)
        .as("the approved task is queryable as an instance of its ontology class")
        .contains(new Term("kb", "HappyTask", taskUri));
  }
}
