package ai.chatur.cortex.spring.query;

import ai.chatur.cortex.CortexSearch;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Web UI for searching the knowledge graph from the navbar search bar. */
@Controller
public class SearchController {

  private final CortexSearch cortex;

  /**
   * Creates the controller.
   *
   * @param cortex the search role used for full-text search
   */
  public SearchController(CortexSearch cortex) {
    this.cortex = cortex;
  }

  /**
   * Renders the results of a full-text search over subject labels, or an empty result set when
   * {@code q} is absent or blank.
   *
   * @param q the search text, or {@code null}
   * @param model receives {@code q} (the search text, or {@code ""} when {@code q} was {@code
   *     null}) and {@code results} (the matching subjects, ranked by relevance)
   * @return the {@code search} view name
   */
  @GetMapping("/search")
  public String search(@RequestParam(value = "q", required = false) String q, Model model) {
    model.addAttribute("q", q == null ? "" : q);
    model.addAttribute("results", q == null || q.isBlank() ? List.of() : cortex.searchSubjects(q));
    return "search";
  }
}
