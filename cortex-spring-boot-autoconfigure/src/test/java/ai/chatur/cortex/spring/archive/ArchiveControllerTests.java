package ai.chatur.cortex.spring.archive;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.CortexArchive;
import ai.chatur.cortex.CortexIngestor;
import ai.chatur.cortex.IngestResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Plain JUnit tests for {@link ArchiveController}, against hand-rolled fakes of its two narrow role
 * dependencies ({@link CortexArchive} and {@link CortexIngestor}) rather than a Spring context.
 */
class ArchiveControllerTests {

  private static final String TTL =
      "@prefix kb: <example://kb/> .\nkb:Task kb:assignedTo kb:Agent .";

  @Test
  void exportAssertionsShouldReturnTurtleWithAttachmentHeader() {
    ArchiveController controller = new ArchiveController(() -> TTL, ttl -> null);

    ResponseEntity<String> response = controller.exportAssertions();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).hasToString("text/turtle");
    assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
    assertThat(response.getHeaders().getContentDisposition().getFilename())
        .as("the download is dated, and is Turtle rather than the TriG it replaced")
        .startsWith("cortex-assertions-")
        .endsWith(".ttl");
    assertThat(response.getBody()).isEqualTo(TTL);
  }

  @Test
  void importShouldStageTheUploadAndRedirectToBranches() throws IOException {
    FakeIngestor ingestor = new FakeIngestor(new IngestResult(true, "branch-1", null));
    ArchiveController controller = new ArchiveController(() -> TTL, ingestor);
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    RedirectView view = controller.importAssertions(upload("assertions.ttl", TTL), attributes);

    assertThat(ingestor.ingested)
        .as("the upload goes through ingest, so it is linted, validated, and staged for review")
        .isEqualTo(TTL);
    assertThat(view.getUrl()).isEqualTo("/branches");
    assertThat(attributes.getFlashAttributes().get("importNotice"))
        .isEqualTo("Staged branch branch-1 for review.");
  }

  @Test
  void importShouldReportWhenNothingIsNovel() throws IOException {
    FakeIngestor ingestor = new FakeIngestor(new IngestResult(true, null, null));
    ArchiveController controller = new ArchiveController(() -> TTL, ingestor);
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    RedirectView view = controller.importAssertions(upload("assertions.ttl", TTL), attributes);

    assertThat(view.getUrl()).isEqualTo("/branches");
    assertThat(attributes.getFlashAttributes().get("importNotice"))
        .as(
            "a valid result with no branch means every statement was already approved — landing on"
                + " an unchanged branch list with no explanation would look like a failure")
        .isEqualTo("Nothing to review: every statement in the upload is already approved.");
    assertThat(attributes.getFlashAttributes()).doesNotContainKey("importError");
  }

  @Test
  void importShouldSurfaceValidationErrors() throws IOException {
    FakeIngestor ingestor =
        new FakeIngestor(new IngestResult(false, null, "Property not found in ontology: kb:nope"));
    ArchiveController controller = new ArchiveController(() -> TTL, ingestor);
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    RedirectView view = controller.importAssertions(upload("assertions.ttl", TTL), attributes);

    assertThat(view.getUrl()).isEqualTo("/branches");
    assertThat(attributes.getFlashAttributes().get("importError"))
        .isEqualTo("Property not found in ontology: kb:nope");
  }

  @Test
  void importShouldRejectATrigUploadWithoutIngestingIt() throws IOException {
    FakeIngestor ingestor = new FakeIngestor(new IngestResult(true, "branch-1", null));
    ArchiveController controller = new ArchiveController(() -> TTL, ingestor);
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    RedirectView view = controller.importAssertions(upload("backup.trig", TTL), attributes);

    assertThat(ingestor.ingested).as("a .trig upload never reaches ingest").isNull();
    assertThat(view.getUrl()).isEqualTo("/branches");
    assertThat(attributes.getFlashAttributes().get("importError"))
        .asString()
        .as("0.1.0 exported .trig backups, so this is the one predictable wrong upload")
        .contains(".trig")
        .contains("Turtle");
  }

  private static MockMultipartFile upload(String filename, String content) {
    return new MockMultipartFile(
        "file", filename, "text/turtle", content.getBytes(StandardCharsets.UTF_8));
  }

  /** Hand-rolled fake of {@link CortexIngestor}, recording what it was handed. */
  private static final class FakeIngestor implements CortexIngestor {
    private final IngestResult result;
    private String ingested;

    FakeIngestor(IngestResult result) {
      this.result = result;
    }

    @Override
    public IngestResult ingest(String ttl) {
      this.ingested = ttl;
      return result;
    }
  }
}
