package ai.chatur.cortex.core.store;

import java.nio.file.Path;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a backup file into the assertions dataset, replacing whatever it currently holds.
 *
 * <p>The inverse of {@link BackupService}: that snapshots the store to gzipped N-Quads, this loads
 * such a snapshot back. It sits beside {@link AssertionStore} (which opens TDB2) and {@link
 * BackupService} (which snapshots it) for the same reason — this is the one place a snapshot is
 * read back into the store.
 *
 * <p>Restore is a <em>wipe-and-load</em>, not a merge: {@link #restore} clears the whole dataset
 * and loads the file over the empty store, so the store ends up byte-for-graph identical to the
 * snapshot — the approved assertions, every staged branch, and the provenance graph, all of which a
 * TDB2 backup carries. The N-Quads a backup contains carry no prefixes, so the ontology's prefixes
 * are re-seeded afterwards exactly as {@link AssertionStore#open} seeds them on a fresh store.
 *
 * <p>Like {@link BackupService} this is deliberately not part of {@link ai.chatur.cortex.Cortex}:
 * it is an operation on the <em>store</em>, replacing its entire contents from a local file, not an
 * operation on the knowledge graph.
 */
public class RestoreService {

  private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

  private final Dataset assertions;
  private final PrefixMapping prefixes;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches, whose
   *     entire contents {@link #restore} replaces
   * @param prefixes the ontology's prefix mapping, re-seeded into the default model after a restore
   *     so assertions and branches serialize with the same abbreviations as a freshly opened store
   */
  public RestoreService(Dataset assertions, PrefixMapping prefixes) {
    this.assertions = assertions;
    this.prefixes = prefixes;
  }

  /**
   * Replaces the entire assertions dataset with the contents of a gzipped N-Quads backup file.
   *
   * <p>The clear, the load, and the prefix re-seeding all run inside a single TDB2 write
   * transaction, so the whole restore is atomic: a parse error partway through the file aborts it
   * and leaves the previous contents intact rather than a half-loaded store.
   *
   * @param backup a gzipped N-Quads file, as {@link BackupService#backup} produces (its {@code
   *     .nq.gz} name is what tells the parser to un-gzip it and read N-Quads)
   * @throws org.apache.jena.riot.RiotException if the file cannot be read or parsed
   */
  public void restore(Path backup) {
    Txn.executeWrite(
        assertions,
        () -> {
          DatasetGraph dsg = assertions.asDatasetGraph();
          dsg.clear();
          RDFDataMgr.read(dsg, backup.toString());
          assertions.getDefaultModel().setNsPrefixes(prefixes);
        });
    log.info("Restored assertions store from backup {}", backup);
  }
}
