package ai.chatur.cortex.core.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link RestoreService} against a real TDB2 store.
 *
 * <p>Uses a real store rather than a fake because what matters — that a restore replaces the whole
 * dataset atomically, across the default graph and the named branch/provenance graphs — is a
 * property of the TDB2 transaction, not of any seam a fake could stand in for.
 */
class RestoreServiceTests {

  private static final String BACKUP_NQUADS =
      "<example://kb/RestoredTask> <example://ontology#assignedTo> <example://kb/RestoredAgent> .\n"
          + "<example://kb/Staged> <example://ontology#p> <example://kb/O> <cortex://provenance> .\n";

  @Test
  void restoreShouldReplaceTheStoreWithTheBackupContents(@TempDir Path location, @TempDir Path work)
      throws IOException {
    Dataset assertions = AssertionStore.open(true, location.toString(), PrefixMapping.Standard);
    addDefaultTriple(assertions, "example://kb/StaleTask");
    addQuad(assertions, "cortex://branch-old", "example://kb/StaleBranch");
    Path backup = gzip(work.resolve("backup_2026-07-18_020000.nq.gz"), BACKUP_NQUADS);

    new RestoreService(assertions, PrefixMapping.Standard).restore(backup);

    Txn.executeRead(
        assertions,
        () -> {
          assertThat(
                  assertions.getDefaultModel().containsResource(resource("example://kb/StaleTask")))
              .as("a restore wipes the store rather than merging into it")
              .isFalse();
          assertThat(assertions.containsNamedModel("cortex://branch-old"))
              .as("named graphs present only in the old store are gone too")
              .isFalse();
          assertThat(
                  assertions
                      .getDefaultModel()
                      .containsResource(resource("example://kb/RestoredTask")))
              .as("the backup's default-graph assertions are loaded")
              .isTrue();
          assertThat(
                  assertions
                      .getNamedModel("cortex://provenance")
                      .containsResource(resource("example://kb/Staged")))
              .as("the backup's named graphs — branches and provenance — are loaded too")
              .isTrue();
          assertThat(assertions.getDefaultModel().getNsPrefixURI("rdfs"))
              .as("the ontology prefixes are re-seeded, as they are on a freshly opened store")
              .isEqualTo(PrefixMapping.Standard.getNsPrefixURI("rdfs"));
        });
  }

  @Test
  void restoreShouldLeaveTheStoreIntactWhenTheBackupCannotBeParsed(
      @TempDir Path location, @TempDir Path work) throws IOException {
    Dataset assertions = AssertionStore.open(true, location.toString(), PrefixMapping.Standard);
    addDefaultTriple(assertions, "example://kb/KeptTask");
    Path corrupt =
        gzip(work.resolve("backup_2026-07-18_030000.nq.gz"), "this is not valid n-quads");

    RestoreService restoreService = new RestoreService(assertions, PrefixMapping.Standard);

    assertThatThrownBy(() -> restoreService.restore(corrupt))
        .as("a corrupt backup fails loudly rather than seeding a broken store")
        .isInstanceOf(RiotException.class);
    Txn.executeRead(
        assertions,
        () ->
            assertThat(
                    assertions
                        .getDefaultModel()
                        .containsResource(resource("example://kb/KeptTask")))
                .as(
                    "clear, load, and re-seed share one transaction, so a failed load rolls the"
                        + " clear back and the previous contents survive")
                .isTrue());
  }

  private static org.apache.jena.rdf.model.Resource resource(String uri) {
    return ModelFactory.createDefaultModel().createResource(uri);
  }

  private static void addDefaultTriple(Dataset assertions, String subject) {
    Txn.executeWrite(
        assertions,
        () ->
            assertions
                .getDefaultModel()
                .add(
                    resource(subject),
                    ModelFactory.createDefaultModel().createProperty("example://ontology#p"),
                    resource("example://kb/O")));
  }

  private static void addQuad(Dataset assertions, String graph, String subject) {
    Txn.executeWrite(
        assertions,
        () ->
            assertions
                .getNamedModel(graph)
                .add(
                    resource(subject),
                    ModelFactory.createDefaultModel().createProperty("example://ontology#p"),
                    resource("example://kb/O")));
  }

  private static Path gzip(Path file, String content) throws IOException {
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write(content.getBytes(StandardCharsets.UTF_8));
    }
    return file;
  }
}
