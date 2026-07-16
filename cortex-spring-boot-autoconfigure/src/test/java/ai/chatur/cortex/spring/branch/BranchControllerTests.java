package ai.chatur.cortex.spring.branch;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.BranchInfo;
import ai.chatur.cortex.BranchStatement;
import ai.chatur.cortex.BranchSubject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Plain JUnit tests for {@link BranchController}, against a hand-rolled fake of its single narrow
 * Phase-3 role dependency ({@link ai.chatur.cortex.CortexBranches}) rather than a Spring context.
 */
class BranchControllerTests {

  @Test
  void listBranchesShouldRenderEveryPendingBranchWithItsSummary() {
    BranchInfo info = new BranchInfo("branch-1", "2024-01-01T00:00:00Z", 3);
    FakeBranches branches =
        new FakeBranches(
            List.of("branch-1"), Map.of("branch-1", info), Map.of(), Set.of("branch-1"));
    BranchController controller = new BranchController(branches);
    Model model = new ExtendedModelMap();

    String view = controller.listBranches(model);

    assertThat(view).isEqualTo("branches");
    @SuppressWarnings("unchecked")
    List<BranchInfo> rendered = (List<BranchInfo>) model.getAttribute("branches");
    assertThat(rendered).containsExactly(info);
  }

  @Test
  void getBranchShouldRenderItsStagedSubjects() {
    BranchSubject subject =
        new BranchSubject(
            "Task",
            "example://kb/Task",
            List.of(
                new BranchStatement(
                    "assignedTo",
                    "example://ontology#assignedTo",
                    "example://kb/Agent",
                    false,
                    null)));
    FakeBranches branches =
        new FakeBranches(
            List.of(), Map.of(), Map.of("branch-1", List.of(subject)), Set.of("branch-1"));
    BranchController controller = new BranchController(branches);
    Model model = new ExtendedModelMap();

    String view = controller.getBranch("branch-1", model);

    assertThat(view).isEqualTo("branch");
    assertThat(model.getAttribute("branch")).isEqualTo("branch-1");
    @SuppressWarnings("unchecked")
    List<BranchSubject> subjects = (List<BranchSubject>) model.getAttribute("subjects");
    assertThat(subjects).containsExactly(subject);
  }

  @Test
  void approveBranchShouldDelegateAndRedirectToAssertions() {
    FakeBranches branches = FakeBranches.withNoBranches();
    BranchController controller = new BranchController(branches);

    RedirectView view = controller.approveBranch("branch-1");

    assertThat(branches.approvedBranch)
        .as("the controller delegates the approval to CortexBranches")
        .isEqualTo("branch-1");
    assertThat(view.getUrl()).isEqualTo("/assertions");
  }

  @Test
  void rejectBranchShouldDelegateAndRedirectToBranches() {
    FakeBranches branches = FakeBranches.withNoBranches();
    BranchController controller = new BranchController(branches);

    RedirectView view = controller.rejectBranch("branch-1");

    assertThat(branches.rejectedBranch)
        .as("the controller delegates the rejection to CortexBranches")
        .isEqualTo("branch-1");
    assertThat(view.getUrl()).isEqualTo("/branches");
  }
}
