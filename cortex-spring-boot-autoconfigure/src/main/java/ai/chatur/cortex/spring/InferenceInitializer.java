package ai.chatur.cortex.spring;

import ai.chatur.cortex.Cortex;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InferenceInitializer {

  private final Cortex cortex;

  public InferenceInitializer(Cortex cortex) {
    this.cortex = cortex;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeInference() {
    cortex.recomputeInference();
  }
}
