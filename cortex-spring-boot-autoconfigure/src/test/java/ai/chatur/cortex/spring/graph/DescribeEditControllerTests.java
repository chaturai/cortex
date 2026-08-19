package ai.chatur.cortex.spring.graph;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.ResourceEdit;
import ai.chatur.cortex.spring.support.FakeIngestor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Plain JUnit tests for {@link DescribeEditController}, against a hand-rolled fake of its single
 * narrow role dependency ({@link ai.chatur.cortex.CortexIngestor}) rather than a Spring context.
 */
class DescribeEditControllerTests {

  private static final ResourceEdit EDIT =
      new ResourceEdit(
          "example://kb/Task",
          null,
          List.of(
              new BranchChange(
                  "example://kb/Task",
                  "example://ontology#assignedTo",
                  "example://kb/Agent",
                  false,
                  null,
                  null,
                  "example://kb/OtherAgent",
                  false)));

  @Test
  void reviseShouldReturnTheBranchToReview() {
    IngestResult staged = new IngestResult(true, "branch-1", null);
    FakeIngestor ingestor = new FakeIngestor(staged);
    DescribeEditController controller = new DescribeEditController(ingestor);

    ResponseEntity<IngestResult> response = controller.revise(EDIT);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(staged);
    assertThat(ingestor.revised())
        .as("the edit is handed to the ingestor untouched, to be staged for review")
        .isSameAs(EDIT);
  }

  @Test
  void reviseShouldReturn200WithNoBranchWhenTheEditChangesNothing() {
    DescribeEditController controller =
        new DescribeEditController(new FakeIngestor(new IngestResult(true, null, null)));

    ResponseEntity<IngestResult> response = controller.revise(EDIT);

    assertThat(response.getStatusCode())
        .as("an edit that changes nothing is not an error; there is simply nothing to review")
        .isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().branch()).isNull();
  }

  @Test
  void reviseShouldReturn422WithTheErrorsWhenTheEditIsRejected() {
    IngestResult rejected =
        new IngestResult(false, null, "a task must be assigned to at least one agent");
    DescribeEditController controller = new DescribeEditController(new FakeIngestor(rejected));

    ResponseEntity<IngestResult> response = controller.revise(EDIT);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().errors())
        .as("the page shows the reviewer why, so the errors have to come back in the body")
        .contains("at least one agent");
  }
}
