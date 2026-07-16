package ai.chatur.cortex.core.store;

import org.apache.jena.query.Dataset;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens the TDB2 dataset holding the approved assertions and the branches pending review. */
public final class AssertionStore {

  private static final Logger log = LoggerFactory.getLogger(AssertionStore.class);

  private AssertionStore() {}

  /**
   * Opens the assertions dataset and seeds its default model with the ontology's prefixes, so
   * assertions and branches serialize using the same abbreviations as the ontology.
   *
   * @param persistent whether to open a TDB2 store on disk rather than in memory
   * @param location the directory of the TDB2 store, used only when {@code persistent}
   * @param prefixes the ontology's prefix mapping to seed the default model with
   * @return the opened, prefix-seeded assertions dataset
   */
  public static Dataset open(boolean persistent, String location, PrefixMapping prefixes) {
    Dataset assertions;
    if (persistent) {
      log.info("Connecting persistent assertions store at {}", location);
      assertions = TDB2Factory.connectDataset(location);
    } else {
      log.info("Using in-memory assertions store");
      assertions = TDB2Factory.createDataset();
    }
    Txn.executeWrite(assertions, () -> assertions.getDefaultModel().setNsPrefixes(prefixes));
    return assertions;
  }
}
