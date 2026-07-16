package ai.chatur.cortex;

/** Rebuilds the statements the knowledge graph derives by rule-based inference. */
public interface CortexInference {

  /**
   * Rebuilds the statements derived by inference from the current assertions.
   *
   * <p>{@link CortexArchive#importAssertions(String) importAssertions} invokes this automatically
   * after restoring the dataset, and the Spring starter invokes it automatically at application
   * startup; {@link CortexBranches#approve(String) approve} extends the closure incrementally
   * instead. Call this directly only when the underlying assertions have been modified through
   * other means.
   */
  void recomputeInference();
}
