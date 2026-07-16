package ai.chatur.cortex.spring.archive;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.CortexArchive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Plain JUnit tests for {@link ArchiveController}, against a hand-rolled fake of its single narrow
 * Phase-3 role dependency ({@link CortexArchive}) rather than a Spring context.
 */
class ArchiveControllerTests {

  @Test
  void exportAssertionsShouldReturnTrigWithAttachmentHeader() {
    FakeArchive archive =
        new FakeArchive("@prefix kb: <example://kb/> .\nkb:Task kb:assignedTo kb:Agent .");
    ArchiveController controller = new ArchiveController(archive);

    ResponseEntity<String> response = controller.exportAssertions();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).hasToString("application/trig");
    assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
    assertThat(response.getHeaders().getContentDisposition().getFilename())
        .as("the download is named for today's date")
        .startsWith("cortex-assertions-");
    assertThat(response.getBody()).isEqualTo(archive.export);
  }

  @Test
  void importAssertionsShouldDelegateAndRedirectToAssertions() throws IOException {
    FakeArchive archive = new FakeArchive("unused");
    ArchiveController controller = new ArchiveController(archive);
    String backup = "@prefix kb: <example://kb/> .\nkb:Task kb:assignedTo kb:Agent .";
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "backup.trig", "application/trig", backup.getBytes(StandardCharsets.UTF_8));

    RedirectView view = controller.importAssertions(file);

    assertThat(archive.imported)
        .as("the uploaded backup is passed through to CortexArchive")
        .isEqualTo(backup);
    assertThat(view.getUrl()).isEqualTo("/assertions");
  }

  /** Hand-rolled fake of {@link CortexArchive}. */
  private static final class FakeArchive implements CortexArchive {
    private final String export;
    private String imported;

    FakeArchive(String export) {
      this.export = export;
    }

    @Override
    public String getAssertions() {
      throw new UnsupportedOperationException("not exercised by ArchiveController");
    }

    @Override
    public String exportAssertions() {
      return export;
    }

    @Override
    public void importAssertions(String trig) {
      this.imported = trig;
    }
  }
}
