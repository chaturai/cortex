package ai.chatur.cortex.spring.archive;

import ai.chatur.cortex.CortexArchive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

/** Web UI for backing up and restoring the assertions dataset. */
@Controller
public class ArchiveController {

  private final CortexArchive archive;

  /**
   * Creates the controller.
   *
   * @param archive the archive role used to export and restore the assertions dataset
   */
  public ArchiveController(CortexArchive archive) {
    this.archive = archive;
  }

  /**
   * Downloads the entire assertions dataset — the approved assertions and every staged branch — as
   * a dated TriG file.
   *
   * @return 200 OK with the dataset as the response body, {@code Content-Type: application/trig},
   *     and a {@code Content-Disposition} attachment header naming the file {@code
   *     cortex-assertions-<today>.trig}
   */
  @GetMapping("/export")
  public ResponseEntity<String> exportAssertions() {
    String filename = "cortex-assertions-" + LocalDate.now() + ".trig";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("application/trig"))
        .body(archive.exportAssertions());
  }

  /**
   * Restores the assertions dataset from an {@link #exportAssertions() exported backup}.
   *
   * @param file the uploaded backup, a dataset serialized in TriG syntax
   * @return a redirect to {@code /assertions}
   * @throws IOException if the uploaded file cannot be read
   */
  @PostMapping("/import")
  public RedirectView importAssertions(@RequestParam("file") MultipartFile file)
      throws IOException {
    archive.importAssertions(new String(file.getBytes(), StandardCharsets.UTF_8));
    return new RedirectView("/assertions");
  }
}
