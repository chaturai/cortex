package ai.chatur.cortex.spring.branch;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchRename;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Plain JUnit tests for {@link BranchEditController}, against a hand-rolled fake of its single
 * narrow Phase-3 role dependency ({@link ai.chatur.cortex.CortexBranches}) rather than a Spring
 * context.
 */
class BranchEditControllerTests {

  @Test
  void updateBranchShouldReturn204WhenTheBranchExists() {
    FakeBranches branches = new FakeBranches(List.of(), Map.of(), Map.of(), Set.of("branch-1"));
    BranchEditController controller = new BranchEditController(branches);
    List<BranchChange> changes =
        List.of(
            new BranchChange(
                "example://kb/Task",
                "example://ontology#assignedTo",
                "example://kb/Agent",
                false,
                null,
                null));

    ResponseEntity<Void> response = controller.updateBranch("branch-1", changes);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(branches.lastUpdatedBranch).isEqualTo("branch-1");
    assertThat(branches.lastUpdateChanges).isEqualTo(changes);
  }

  @Test
  void updateBranchShouldReturn404WhenTheBranchIsMissing() {
    FakeBranches branches = FakeBranches.withNoBranches();
    BranchEditController controller = new BranchEditController(branches);

    ResponseEntity<Void> response = controller.updateBranch("missing-branch", List.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void renameBranchSubjectsShouldReturn204WhenTheBranchExists() {
    FakeBranches branches = new FakeBranches(List.of(), Map.of(), Map.of(), Set.of("branch-1"));
    BranchEditController controller = new BranchEditController(branches);
    List<BranchRename> renames =
        List.of(new BranchRename("example://kb/Agent", "example://kb/RenamedAgent"));

    ResponseEntity<Void> response = controller.renameBranchSubjects("branch-1", renames);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(branches.lastRenamedBranch).isEqualTo("branch-1");
    assertThat(branches.lastRenames).isEqualTo(renames);
  }

  @Test
  void renameBranchSubjectsShouldReturn404WhenTheBranchIsMissing() {
    FakeBranches branches = FakeBranches.withNoBranches();
    BranchEditController controller = new BranchEditController(branches);

    ResponseEntity<Void> response = controller.renameBranchSubjects("missing-branch", List.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
