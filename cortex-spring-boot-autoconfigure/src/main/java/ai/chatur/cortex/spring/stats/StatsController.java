package ai.chatur.cortex.spring.stats;

import ai.chatur.cortex.Cortex;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Web UI home page showing statistics of the knowledge graph. */
@Controller
public class StatsController {

  private final Cortex cortex;

  public StatsController(Cortex cortex) {
    this.cortex = cortex;
  }

  @GetMapping("/")
  public String getStats(Model model) {
    model.addAttribute("stats", cortex.getStats());
    return "home";
  }
}
