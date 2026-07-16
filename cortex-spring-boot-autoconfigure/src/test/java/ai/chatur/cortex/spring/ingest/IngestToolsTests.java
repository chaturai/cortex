package ai.chatur.cortex.spring.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.IngestResult;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Plain JUnit test for {@link IngestTools}, against a hand-rolled lambda fake of its single narrow
 * Phase-3 role dependency ({@link ai.chatur.cortex.CortexIngestor}) rather than a Spring context.
 */
class IngestToolsTests {

  @Test
  void ingestShouldDelegateToCortexIngest() {
    IngestResult expected = new IngestResult(true, "branch-1", null);
    AtomicReference<String> lastTtl = new AtomicReference<>();
    IngestTools tools =
        new IngestTools(
            ttl -> {
              lastTtl.set(ttl);
              return expected;
            });

    IngestResult result = tools.ingest("kb:Task kb:assignedTo kb:Agent .");

    assertThat(result).isSameAs(expected);
    assertThat(lastTtl.get()).isEqualTo("kb:Task kb:assignedTo kb:Agent .");
  }
}
