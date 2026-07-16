package ai.chatur.cortex.spring.stats;

import ai.chatur.cortex.CortexStatistics;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Web UI home page showing statistics of the knowledge graph. */
@Controller
public class StatsController {

  private final CortexStatistics cortex;

  /**
   * Creates the controller.
   *
   * @param cortex the statistics role used to compute the knowledge graph's statistics
   */
  public StatsController(CortexStatistics cortex) {
    this.cortex = cortex;
  }

  /**
   * Renders the knowledge graph's home page with its current statistics.
   *
   * @param model receives {@code stats} (the {@link ai.chatur.cortex.CortexStats} snapshot)
   * @return the {@code home} view name
   */
  @GetMapping("/")
  public String getStats(Model model) {
    model.addAttribute("stats", cortex.getStats());
    return "home";
  }
}
