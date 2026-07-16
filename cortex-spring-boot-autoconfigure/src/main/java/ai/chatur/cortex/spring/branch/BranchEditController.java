package ai.chatur.cortex.spring.branch;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchRename;
import ai.chatur.cortex.CortexBranches;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON API consumed by {@code branch.js} to apply reviewer edits to a branch pending review, in
 * place, without a page reload.
 */
@RestController
public class BranchEditController {

  private final CortexBranches branches;

  /**
   * Creates the controller.
   *
   * @param branches the branches role used to apply edits to a pending branch
   */
  public BranchEditController(CortexBranches branches) {
    this.branches = branches;
  }

  /**
   * Applies deletions and object edits to the assertions staged on a branch.
   *
   * @param branch the branch name
   * @param changes the changes to apply, as JSON
   * @return 204 No Content if the branch existed and the changes were applied, 404 Not Found if the
   *     branch does not exist
   */
  @PostMapping("/branches/{branch}/update")
  public ResponseEntity<Void> updateBranch(
      @PathVariable("branch") String branch, @RequestBody List<BranchChange> changes) {
    return branches.updateBranch(branch, changes)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  /**
   * Renames subjects staged on a branch.
   *
   * @param branch the branch name
   * @param renames the renames to apply, as JSON
   * @return 204 No Content if the branch existed and the renames were applied, 404 Not Found if the
   *     branch does not exist
   */
  @PostMapping("/branches/{branch}/rename")
  public ResponseEntity<Void> renameBranchSubjects(
      @PathVariable("branch") String branch, @RequestBody List<BranchRename> renames) {
    return branches.renameBranchSubjects(branch, renames)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }
}
