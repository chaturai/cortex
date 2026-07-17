package ai.chatur.cortex.core.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDBException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link BackupService} against a real TDB2 store.
 *
 * <p>Uses a real store rather than a fake because the whole point of this service is a TDB2 admin
 * operation, and the constraint that matters — that it works on disk and not in memory — exists
 * nowhere but in TDB2 itself.
 */
class BackupServiceTests {

  @Test
  void backupShouldWriteAGzippedSnapshotBesideAPersistentStore(@TempDir Path location)
      throws IOException {
    Dataset assertions = AssertionStore.open(true, location.toString(), PrefixMapping.Standard);
    addTriple(assertions);

    String path = new BackupService(assertions).backup();

    assertThat(Path.of(path))
        .as("the backup is written where TDB2 says it was")
        .isRegularFile()
        .hasParent(location.resolve("Backups"));
    assertThat(path).as("TDB2 names backups by timestamp").endsWith(".nq.gz");
    assertThat(gunzip(Path.of(path)))
        .as("the snapshot carries the assertions, as N-Quads")
        .contains("example://kb/BackedUpTask");
  }

  @Test
  void backupShouldFailForAnInMemoryStore() {
    Dataset assertions = AssertionStore.open(false, null, PrefixMapping.Standard);
    addTriple(assertions);

    BackupService backupService = new BackupService(assertions);

    // This is the constraint the whole backup feature is shaped around: TDB2 guards backup() with
    // DatabaseOps.checkSupportsAdmin, which throws when the dataset has no container path — always
    // the case in memory. It is why CortexBackupAutoConfiguration refuses to start without
    // cortex.persistent=true rather than discovering this at the first scheduled run, and why
    // backup() is not on the Cortex interface, whose default build is in-memory.
    assertThatThrownBy(backupService::backup)
        .as("an in-memory TDB2 dataset has no location to write a backup beside")
        .isInstanceOf(TDBException.class)
        .hasMessageContaining("admin operations");
  }

  private static void addTriple(Dataset assertions) {
    Txn.executeWrite(
        assertions,
        () ->
            assertions
                .getDefaultModel()
                .add(
                    ModelFactory.createDefaultModel().createResource("example://kb/BackedUpTask"),
                    ModelFactory.createDefaultModel()
                        .createProperty("example://ontology#assignedTo"),
                    ModelFactory.createDefaultModel()
                        .createResource("example://kb/BackedUpAgent")));
  }

  private static String gunzip(Path file) throws IOException {
    try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
      return new String(in.readAllBytes());
    }
  }
}
