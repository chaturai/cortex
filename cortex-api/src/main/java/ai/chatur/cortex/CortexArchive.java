package ai.chatur.cortex;

/** Exports the approved assertions as a portable RDF document. */
public interface CortexArchive {

  /**
   * Exports the approved assertions — the knowledge graph as it stands after review.
   *
   * <p>Only approved statements are exported: assertions still staged on a branch, and the
   * provenance recorded for approved ones, are both excluded. The result is therefore a plain
   * instance-data document, expressed in terms of {@link CortexOntology#getOntology() the ontology}
   * but not carrying it, which can be read, diffed, or fed back through {@link
   * CortexIngestor#ingest(String) ingest}.
   *
   * <p>For backing the graph up, see {@link CortexBackup#backup()}, which captures the whole
   * dataset — staged branches and provenance included — rather than just the approved assertions.
   *
   * @return the approved assertions serialized in Turtle syntax
   */
  String exportAssertions();
}
