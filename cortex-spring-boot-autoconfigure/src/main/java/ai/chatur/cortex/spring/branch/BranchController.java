package ai.chatur.cortex.spring.branch;

import ai.chatur.cortex.CortexBranches;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Web UI for reviewing branches staged by ingestion: listing branches pending review, viewing a
 * single branch's staged statements, and approving or rejecting it.
 */
@Controller
public class BranchController {

  private final CortexBranches branches;

  /**
   * Creates the controller.
   *
   * @param branches the branches role used to list, inspect, and resolve pending branches
   */
  public BranchController(CortexBranches branches) {
    this.branches = branches;
  }

  /**
   * Lists every branch pending review with its provenance summary.
   *
   * @param model receives {@code branches}, the {@link ai.chatur.cortex.BranchInfo} of every
   *     pending branch
   * @return the {@code branches} view name
   */
  @GetMapping("/branches")
  public String listBranches(Model model) {
    model.addAttribute(
        "branches", branches.listBranches().stream().map(branches::getBranchInfo).toList());
    return "branches";
  }

  /**
   * Renders the assertions staged on a single branch, grouped by subject.
   *
   * @param branch the branch name
   * @param model receives {@code branch} (the branch name) and {@code subjects} (the {@link
   *     ai.chatur.cortex.BranchSubject}s staged on it)
   * @return the {@code branch} view name
   */
  @GetMapping("/branches/{branch}")
  public String getBranch(@PathVariable("branch") String branch, Model model) {
    model.addAttribute("branch", branch);
    model.addAttribute("subjects", branches.getBranchSubjects(branch));
    return "branch";
  }

  /**
   * Merges a branch's staged assertions into the knowledge graph.
   *
   * @param branch the branch name
   * @return a redirect to {@code /assertions}
   */
  @PostMapping("/branches/{branch}/approve")
  public RedirectView approveBranch(@PathVariable("branch") String branch) {
    branches.approve(branch);
    return new RedirectView("/assertions");
  }

  /**
   * Rejects a branch, discarding its staged assertions.
   *
   * @param branch the branch name
   * @return a redirect to {@code /branches}
   */
  @PostMapping("/branches/{branch}/reject")
  public RedirectView rejectBranch(@PathVariable("branch") String branch) {
    branches.reject(branch);
    return new RedirectView("/branches");
  }
}
