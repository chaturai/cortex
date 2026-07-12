package ai.chatur.cortex.spring.inference;

import ai.chatur.cortex.Cortex;
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

  private final Cortex cortex;

  public InferenceInitializer(Cortex cortex) {
    this.cortex = cortex;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeInference() {
    try {
      cortex.recomputeInference();
    } catch (RuntimeException e) {
      log.error("Failed to compute inference on startup", e);
    }
  }
}
