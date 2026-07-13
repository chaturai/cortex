package ai.chatur.cortex.spring.query;

import ai.chatur.cortex.Cortex;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Web UI for searching the knowledge graph from the navbar search bar. */
@Controller
public class SearchController {

  private final Cortex cortex;

  public SearchController(Cortex cortex) {
    this.cortex = cortex;
  }

  @GetMapping("/search")
  public String search(@RequestParam(value = "q", required = false) String q, Model model) {
    model.addAttribute("q", q == null ? "" : q);
    model.addAttribute("results", q == null || q.isBlank() ? List.of() : cortex.searchSubjects(q));
    return "search";
  }
}
