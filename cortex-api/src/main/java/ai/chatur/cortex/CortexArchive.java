package ai.chatur.cortex;

/** Exports and restores the assertions dataset for backup. */
public interface CortexArchive {

  /**
   * Returns all approved assertions in the knowledge graph.
   *
   * @return the assertions serialized in TriG syntax
   */
  String getAssertions();

  /**
   * Exports the entire assertions dataset — the approved assertions and every staged branch — for
   * backup.
   *
   * @return the dataset serialized in TriG syntax
   */
  String exportAssertions();

  /**
   * Restores the assertions dataset from an {@link #exportAssertions() exported backup}, replacing
   * the approved assertions and every staged branch, and recomputes inference.
   *
   * <p>If the backup cannot be parsed, the dataset is left untouched.
   *
   * @param trig the backup, a dataset serialized in TriG syntax
   */
  void importAssertions(String trig);
}
