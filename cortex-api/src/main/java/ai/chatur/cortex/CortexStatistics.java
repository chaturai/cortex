package ai.chatur.cortex;

/** Reports size and activity statistics for the knowledge graph. */
public interface CortexStatistics {

  /**
   * Returns a snapshot of the size and activity of the knowledge graph.
   *
   * @return the current statistics
   */
  CortexStats getStats();
}
