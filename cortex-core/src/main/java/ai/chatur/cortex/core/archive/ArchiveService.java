package ai.chatur.cortex.core.archive;

import ai.chatur.cortex.core.jena.Rdf;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;

/** Exports the approved assertions as a portable RDF document. */
public class ArchiveService {

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
   * Exports the approved assertions.
   *
   * <p>Reads the default graph only, so staged branches and the provenance graph — which live in
   * named graphs — are excluded. The default graph's prefixes are seeded from the ontology when the
   * store is opened, so the Turtle comes out abbreviated the same way the ontology is.
   *
   * <p>This is not a backup: see {@link ai.chatur.cortex.core.store.BackupService#backup()} for
   * that.
   *
   * @return the approved assertions serialized in Turtle syntax
   */
  public String exportAssertions() {
    return Rdf.writeReading(assertions, assertions::getDefaultModel, Lang.TTL);
  }
}
