package ai.chatur.cortex;

/** Rebuilds the statements the knowledge graph derives by rule-based inference. */
public interface CortexInference {

  /**
   * Rebuilds the statements derived by inference from the current assertions.
   *
   * <p>The Spring starter invokes this automatically at application startup; {@link
   * CortexBranches#approve(String) approve} extends the closure incrementally instead. Call this
   * directly only when the underlying assertions have been modified through other means.
   */
  void recomputeInference();
}
