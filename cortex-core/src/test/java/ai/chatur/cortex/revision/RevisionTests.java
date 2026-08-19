package ai.chatur.cortex.revision;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchInfo;
import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.ResourceEdit;
import ai.chatur.cortex.StatementOrigin;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for editing assertions that are <em>already approved</em>: the retractions a
 * revision stages alongside its additions, the review it still has to go through, and what
 * approving one does to the knowledge graph and to the inference closure.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class RevisionTests {

  private static final String ASSIGNED_TO = "example://ontology#assignedTo";
  private static final String LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
  private static final String TASK = "example://kb/Task1";
  private static final String AGENT = "example://kb/Agent1";

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void shouldStageAnEditAsAnAdditionAndARetraction() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        """);

    IngestResult result = revise(edit(ASSIGNED_TO, AGENT, "example://kb/Agent2"));

    assertThat(result.valid()).isTrue();
    BranchInfo info = cortex.getBranchInfo(result.branch());
    assertThat(info.additions()).as("the replacement is staged for addition").isEqualTo(1);
    assertThat(info.retractions()).as("the value it replaces is staged for removal").isEqualTo(1);
    assertThat(asserted(TASK))
        .as(
            "an edit is a proposal like any other: the approved assertions do not change until"
                + " someone approves the branch")
        .containsExactly(AGENT);
  }

  @Test
  void shouldReplaceTheValueWhenTheRevisionIsApproved() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        """);

    cortex.approve(revise(edit(ASSIGNED_TO, AGENT, "example://kb/Agent2")).branch());

    assertThat(asserted(TASK))
        .as("the old value is gone rather than left standing beside the new one")
        .containsExactly("example://kb/Agent2");
  }

  @Test
  void shouldShrinkTheInferenceClosureWhenAnApprovalRetracts() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        """);
    assertThat(cortex.describe(AGENT))
        .as("the range rule concludes the assignee is an Agent")
        .anySatisfy(
            statement -> assertThat(statement.origin()).isEqualTo(StatementOrigin.INFERRED));

    cortex.approve(revise(edit(ASSIGNED_TO, AGENT, null)).branch());

    assertThat(cortex.describe(AGENT))
        .as(
            "the conclusion goes when its premise does — the closure can only be extended"
                + " incrementally, so a retraction has to recompute it rather than add to it")
        .isEmpty();
  }

  @Test
  void shouldStageAnAddedStatement() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        """);

    IngestResult result =
        cortex.revise(
            new ResourceEdit(
                TASK,
                null,
                List.of(
                    new BranchChange(
                        TASK, "rdfs:label", null, false, null, null, "write the report", false))));

    assertThat(result.valid()).isTrue();
    assertThat(cortex.getBranchInfo(result.branch()).retractions())
        .as("an addition retracts nothing")
        .isZero();
    cortex.approve(result.branch());
    assertThat(asserted(TASK)).contains("write the report");
  }

  @Test
  void shouldAddAValueAsAResourceOnlyWhenItNamesOne() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        """);

    cortex.approve(
        cortex
            .revise(
                new ResourceEdit(
                    TASK,
                    null,
                    List.of(
                        new BranchChange(
                            TASK, ":assignedTo", null, false, null, null, "kb:Agent2", false),
                        new BranchChange(
                            TASK,
                            "rdfs:label",
                            null,
                            false,
                            null,
                            null,
                            "note: call back",
                            false))))
            .branch());

    List<ProvenancedStatement> statements = cortex.describe(TASK);
    assertThat(statements)
        .as("a declared prefix that expands names a resource")
        .anySatisfy(
            statement -> assertThat(statement.object().uri()).isEqualTo("example://kb/Agent2"));
    assertThat(statements)
        .as(
            "prose that merely looks like a prefixed name stays a literal — there is nothing"
                + " downstream that would catch an object IRI nobody can resolve")
        .anySatisfy(
            statement -> {
              assertThat(statement.object().localName()).isEqualTo("note: call back");
              assertThat(statement.literal()).isTrue();
            });
  }

  @Test
  void shouldRewriteEveryReferenceWhenAResourceIsRenamed() {
    approve(
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        kb:Agent1 rdfs:label "the agent" .
        """);

    cortex.approve(
        cortex.revise(new ResourceEdit(AGENT, "example://kb/Agent9", List.of())).branch());

    assertThat(asserted(TASK))
        .as("the reference to the renamed resource follows it")
        .containsExactly("example://kb/Agent9");
    assertThat(asserted("example://kb/Agent9"))
        .as("and so does everything the renamed resource itself said")
        .containsExactly("the agent");
    assertThat(cortex.describe(AGENT)).as("nothing is left behind at the old IRI").isEmpty();
  }

  @Test
  void shouldRenameAndEditInOneRevision() {
    approve(
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix kb: <example://kb/> .

        kb:Agent1 rdfs:label "the agent" .
        """);

    cortex.approve(
        cortex
            .revise(
                new ResourceEdit(
                    AGENT,
                    "example://kb/Agent9",
                    List.of(
                        new BranchChange(
                            AGENT,
                            LABEL,
                            "the agent",
                            true,
                            null,
                            null,
                            "the other agent",
                            false))))
            .branch());

    assertThat(asserted("example://kb/Agent9"))
        .as(
            "the rename is applied to the assertions as the edit leaves them, so the renamed"
                + " resource carries the edited value rather than the one it replaced")
        .containsExactly("the other agent");
  }

  @Test
  void shouldRejectARevisionThatWouldBreakTheShapes() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 a :Task ;
            :assignedTo kb:Agent1 .
        kb:Agent1 a :Agent .
        """);

    IngestResult result = revise(edit(ASSIGNED_TO, AGENT, null));

    assertThat(result.valid())
        .as(
            "the shapes are validated against the assertions as the revision would leave them, so"
                + " a retraction that takes away what was satisfying a constraint is caught —"
                + " validating a union, as an ingest does, could only ever see it satisfied")
        .isFalse();
    assertThat(result.errors()).contains("a task must be assigned to at least one agent");
    assertThat(result.branch()).isNull();
    assertThat(cortex.listBranches()).as("a rejected revision stages nothing").isEmpty();
    assertThat(asserted(TASK)).contains(AGENT);
  }

  @Test
  void shouldRejectAnAdditionUsingAPropertyTheOntologyDoesNotDeclare() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        """);

    IngestResult result =
        cortex.revise(
            new ResourceEdit(
                TASK,
                null,
                List.of(
                    new BranchChange(
                        TASK,
                        "example://ontology#dueOn",
                        null,
                        false,
                        null,
                        null,
                        "tomorrow",
                        false))));

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("Property not found in ontology");
    assertThat(cortex.listBranches()).isEmpty();
  }

  @Test
  void shouldStageNothingWhenTheRevisionChangesNothing() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        """);

    IngestResult unchanged = revise(edit(ASSIGNED_TO, AGENT, AGENT));
    IngestResult absent = revise(edit(ASSIGNED_TO, "example://kb/NeverApproved", null));

    assertThat(unchanged.valid()).isTrue();
    assertThat(unchanged.branch())
        .as("a statement the same revision retracts and re-asserts is left alone")
        .isNull();
    assertThat(absent.valid()).isTrue();
    assertThat(absent.branch())
        .as("retracting a statement that is not approved is not something to review")
        .isNull();
    assertThat(cortex.listBranches()).isEmpty();
  }

  @Test
  void shouldDiscardBothStagedGraphsWhenARevisionIsRejected() {
    approve(
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task1 :assignedTo kb:Agent1 .
        """);
    String branch = revise(edit(ASSIGNED_TO, AGENT, "example://kb/Agent2")).branch();

    cortex.reject(branch);

    assertThat(cortex.listBranches()).isEmpty();
    assertThat(asserted(TASK))
        .as("rejecting a revision retracts nothing, exactly as it approves nothing")
        .containsExactly(AGENT);
  }

  private void approve(String ttl) {
    cortex.approve(cortex.ingest(ttl).branch());
  }

  private IngestResult revise(BranchChange change) {
    return cortex.revise(new ResourceEdit(TASK, null, List.of(change)));
  }

  private BranchChange edit(String predicate, String object, String newObject) {
    return new BranchChange(TASK, predicate, object, false, null, null, newObject, false);
  }

  /** The objects of the statements about a resource that a human approved, in display form. */
  private List<String> asserted(String uri) {
    return cortex.describe(uri).stream()
        .filter(statement -> statement.origin() == StatementOrigin.ASSERTED)
        .map(
            statement ->
                statement.object().uri() != null
                    ? statement.object().uri()
                    : statement.object().localName())
        .toList();
  }
}
