package ai.chatur.cortex.spring.graph;

import ai.chatur.cortex.CortexIngestor;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.ResourceEdit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON API consumed by {@code describe.js} to propose edits to an already-approved resource from
 * the describe page.
 *
 * <p>Unlike {@link ai.chatur.cortex.spring.branch.BranchEditController}, which applies a reviewer's
 * edits to a branch in place, this one changes nothing: it stages the edit on a branch of its own
 * and hands the branch name back, so the caller can send the author to the review page. The
 * approved assertions are only ever reached by approving that branch.
 */
@RestController
public class DescribeEditController {

  private final CortexIngestor ingestor;

  /**
   * Creates the controller.
   *
   * @param ingestor the ingestor role the proposed edit is staged through
   */
  public DescribeEditController(CortexIngestor ingestor) {
    this.ingestor = ingestor;
  }

  /**
   * Stages a proposed edit to an approved resource on a branch for review.
   *
   * @param edit the proposed edit, as JSON
   * @return 200 OK with the outcome — carrying the name of the branch to review, or no branch if
   *     the edit would change nothing — or 422 Unprocessable Content with the lint or validation
   *     errors if the edit was rejected
   */
  @PostMapping("/describe/revise")
  public ResponseEntity<IngestResult> revise(@RequestBody ResourceEdit edit) {
    IngestResult result = ingestor.revise(edit);
    return result.valid()
        ? ResponseEntity.ok(result)
        : ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(result);
  }
}
