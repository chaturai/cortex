package ai.chatur.cortex.spring.stats;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.CortexStatistics;
import ai.chatur.cortex.CortexStats;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/**
 * Plain JUnit test for {@link StatsController}, against a hand-rolled fake of its single narrow
 * Phase-3 role dependency ({@link CortexStatistics}) rather than a Spring context.
 */
class StatsControllerTests {

  @Test
  void getStatsShouldRenderTheStatsSnapshot() {
    CortexStats stats = new CortexStats(1, 2, 3, 4, 5, 6, 7);
    StatsController controller = new StatsController(() -> stats);
    Model model = new ExtendedModelMap();

    String view = controller.getStats(model);

    assertThat(view).isEqualTo("home");
    assertThat(model.getAttribute("stats")).isEqualTo(stats);
  }
}
