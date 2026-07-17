package ai.chatur.cortex.spring.archive;

import ai.chatur.cortex.CortexArchive;
import ai.chatur.cortex.CortexIngestor;
import ai.chatur.cortex.IngestResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Web UI for exporting the approved assertions and importing a Turtle document back in.
 *
 * <p>Import is <strong>not</strong> a restore. It goes through {@link CortexIngestor#ingest(String)
 * ingest} like any other incoming assertions — linted, SHACL-validated, reduced to what is novel,
 * and staged on a branch — so an upload can no longer replace the graph, and lands on {@code
 * /branches} for a human to approve rather than silently taking effect.
 */
@Controller
public class ArchiveController {

  private final CortexArchive archive;
  private final CortexIngestor ingestor;

  /**
   * Creates the controller.
   *
   * @param archive the archive role used to export the approved assertions
   * @param ingestor the ingestor role an uploaded document is staged through
   */
  public ArchiveController(CortexArchive archive, CortexIngestor ingestor) {
    this.archive = archive;
    this.ingestor = ingestor;
  }

  /**
   * Downloads the approved assertions as a dated Turtle file.
   *
   * @return 200 OK with the assertions as the response body, {@code Content-Type: text/turtle}, and
   *     a {@code Content-Disposition} attachment header naming the file {@code
   *     cortex-assertions-<today>.ttl}
   */
  @GetMapping("/export")
  public ResponseEntity<String> exportAssertions() {
    String filename = "cortex-assertions-" + LocalDate.now() + ".ttl";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("text/turtle"))
        .body(archive.exportAssertions());
  }

  /**
   * Stages an uploaded Turtle document on a branch for review.
   *
   * <p>The extension check is not about trusting the upload — {@code ingest} parses it and rejects
   * anything that is not Turtle regardless. It exists so the one predictable wrong upload gets a
   * comprehensible answer: Cortex 0.1.0 exported {@code .trig} whole-dataset backups, and a raw
   * parser error is not a useful way to learn those are no longer accepted.
   *
   * @param file the uploaded assertions, in Turtle syntax
   * @param redirectAttributes receives {@code importNotice} on success or {@code importError} on
   *     failure, rendered by the branches view
   * @return a redirect to {@code /branches}
   * @throws IOException if the uploaded file cannot be read
   */
  @PostMapping("/import")
  public RedirectView importAssertions(
      @RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes)
      throws IOException {
    String filename = file.getOriginalFilename();
    if (filename != null && !filename.toLowerCase(Locale.ROOT).endsWith(".ttl")) {
      redirectAttributes.addFlashAttribute(
          "importError",
          "Import accepts Turtle (.ttl) only, but got \""
              + filename
              + "\". Cortex 0.1.0 exported TriG (.trig) whole-dataset backups; those are no longer"
              + " accepted, because an import is now reviewed like any other ingest rather than"
              + " replacing the graph.");
      return new RedirectView("/branches");
    }
    IngestResult result = ingestor.ingest(new String(file.getBytes(), StandardCharsets.UTF_8));
    if (!result.valid()) {
      redirectAttributes.addFlashAttribute("importError", result.errors());
    } else if (result.branch() == null) {
      redirectAttributes.addFlashAttribute(
          "importNotice", "Nothing to review: every statement in the upload is already approved.");
    } else {
      redirectAttributes.addFlashAttribute(
          "importNotice", "Staged branch " + result.branch() + " for review.");
    }
    return new RedirectView("/branches");
  }
}
