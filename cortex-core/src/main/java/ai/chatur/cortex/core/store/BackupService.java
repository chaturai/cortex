package ai.chatur.cortex.core.store;

import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.DatabaseMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes a TDB2 snapshot of the assertions dataset.
 *
 * <p>Sits beside {@link AssertionStore} deliberately: that is the one place TDB2 is opened, this is
 * the one place it is snapshotted.
 *
 * <p>Neither is part of {@link ai.chatur.cortex.Cortex}, and this one is not because a backup is an
 * operation on the <em>store</em> rather than on the knowledge graph: it is TDB2-specific, it
 * returns a local filesystem path, and it works only against an on-disk store. Putting it on {@code
 * Cortex} would give every graph — including every in-memory one {@link
 * ai.chatur.cortex.CortexBuilder} builds by default — a method that throws unless the store happens
 * to be persistent, which no other method on that interface does.
 *
 * <p><strong>Requires a persistent store.</strong> {@link DatabaseMgr#backup} is a TDB2 admin
 * operation guarded by {@code DatabaseOps.checkSupportsAdmin}, which throws {@link
 * org.apache.jena.tdb2.TDBException TDBException} ({@code "Dataset does not support admin
 * operations"}) when the dataset has no container path — exactly the case for the in-memory store
 * {@link AssertionStore#open} returns when {@code persistent} is {@code false}.
 */
public class BackupService {

  private static final Logger log = LoggerFactory.getLogger(BackupService.class);

  private final Dataset assertions;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches; must be
   *     backed by an on-disk TDB2 store, since an in-memory one cannot be backed up
   */
  public BackupService(Dataset assertions) {
    this.assertions = assertions;
  }

  /**
   * Writes a snapshot of the whole assertions dataset — the approved assertions, every staged
   * branch, and the provenance graph — as gzipped N-Quads, under a {@code Backups/} directory
   * beside the store.
   *
   * <p>TDB2 runs the serialization inside its own read transaction, so this must not be wrapped in
   * another one, and concurrent ingestion and approval are not locked out while it runs.
   *
   * <p>Nothing here deletes anything: every call adds a file, and retention of those files is the
   * caller's business.
   *
   * @return the path of the file written, as TDB2 reports it — relative to the working directory
   *     when the store's location is itself relative
   * @throws org.apache.jena.tdb2.TDBException if the assertions are held in memory, and so have no
   *     container path to write a backup beside
   */
  public String backup() {
    String path = DatabaseMgr.backup(assertions.asDatasetGraph());
    log.info("Wrote TDB2 backup to {}", path);
    return path;
  }
}
