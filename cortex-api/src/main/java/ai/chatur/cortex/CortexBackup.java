package ai.chatur.cortex;

/**
 * Snapshots the assertions store to a durable local file.
 *
 * <p>Implemented in {@code cortex-core} by {@code BackupService}, which wraps TDB2's own backup;
 * declared here in {@code cortex-api} so the capability can be referred to without depending on
 * Jena. Deliberately not part of {@link Cortex}: a backup is an operation on the store, not on the
 * knowledge graph, and it exists only for a persistent, on-disk store.
 */
public interface CortexBackup {

  /**
   * Writes a snapshot of the whole assertions store to a local file.
   *
   * @return the path of the file written
   */
  String backup();
}
