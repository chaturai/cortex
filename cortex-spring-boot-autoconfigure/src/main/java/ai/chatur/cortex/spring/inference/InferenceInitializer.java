package ai.chatur.cortex.spring.inference;

import ai.chatur.cortex.CortexInference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Computes inference once the application is ready, so that queries against a freshly started —
 * possibly persistent — knowledge graph see inferred statements without waiting for the first
 * branch approval.
 */
public class InferenceInitializer {

  private static final Logger log = LoggerFactory.getLogger(InferenceInitializer.class);

  private final CortexInference cortex;

  /**
   * Creates the initializer.
   *
   * @param cortex the inference role used to recompute inference
   */
  public InferenceInitializer(CortexInference cortex) {
    this.cortex = cortex;
  }

  /**
   * Recomputes inference over the approved assertions. Runs once, on {@link ApplicationReadyEvent};
   * failures are logged rather than rethrown, since a failure here must not prevent the application
   * from starting.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void initializeInference() {
    try {
      cortex.recomputeInference();
    } catch (RuntimeException e) {
      log.error("Failed to compute inference on startup", e);
    }
  }
}
