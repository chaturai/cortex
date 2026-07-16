package ai.chatur.cortex.core.archive;

import ai.chatur.cortex.core.jena.Rdf;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports and restores the assertions dataset — the approved assertions and every staged branch —
 * for backup.
 */
public class ArchiveService {

  private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);

  private final Dataset assertions;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   */
  public ArchiveService(Dataset assertions) {
    this.assertions = assertions;
  }

  /**
   * Returns all approved assertions.
   *
   * @return the default graph serialized in TriG syntax
   */
  public String getAssertions() {
    return Rdf.writeReading(assertions, assertions::getDefaultModel, Lang.TRIG);
  }

  /**
   * Exports the entire assertions dataset — the approved assertions and every staged branch — for
   * backup.
   *
   * @return the dataset serialized in TriG syntax
   */
  public String exportAssertions() {
    return Rdf.writeReading(assertions, Lang.TRIG);
  }

  /**
   * Restores the assertions dataset from an {@link #exportAssertions() exported backup}, replacing
   * the approved assertions and every staged branch.
   *
   * <p>The replacement happens in a single transaction: if the backup cannot be parsed, the
   * transaction is aborted and the dataset is left untouched.
   *
   * @param trig the backup, a dataset serialized in TriG syntax
   */
  public void importAssertions(String trig) {
    Txn.executeWrite(
        assertions,
        () -> {
          assertions.asDatasetGraph().clear();
          RDFParser.fromString(trig, Lang.TRIG).parse(assertions.asDatasetGraph());
        });
    Txn.executeRead(
        assertions,
        () ->
            log.info(
                "Imported assertions dataset with {} approved triples",
                assertions.getDefaultModel().size()));
  }
}
