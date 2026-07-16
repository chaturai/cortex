package ai.chatur.cortex.spring.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.chatur.cortex.CortexInference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Plain JUnit tests for {@link InferenceInitializer}, against a hand-rolled lambda fake of its
 * single narrow Phase-3 role dependency ({@link CortexInference}) rather than a Spring context.
 */
class InferenceInitializerTests {

  @Test
  void initializeInferenceShouldDelegateToRecomputeInference() {
    AtomicBoolean recomputed = new AtomicBoolean(false);
    InferenceInitializer initializer = new InferenceInitializer(() -> recomputed.set(true));

    initializer.initializeInference();

    assertThat(recomputed)
        .as("the initializer delegates to CortexInference.recomputeInference")
        .isTrue();
  }

  @Test
  void initializeInferenceShouldSwallowRuntimeExceptionFromRecomputeInference() {
    InferenceInitializer initializer =
        new InferenceInitializer(
            () -> {
              throw new RuntimeException("reasoner blew up");
            });

    // A failure to compute inference at startup must not fail application startup — it is logged
    // instead (see the class javadoc and R2 in the refactor plan's risk register).
    assertThatCode(initializer::initializeInference)
        .as("a RuntimeException from recomputeInference is caught and logged, not propagated")
        .doesNotThrowAnyException();
  }
}
