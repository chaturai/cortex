package ai.chatur.cortex.spring.ontology;

import ai.chatur.cortex.CortexOntology;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Web UI page rendering the ontology in Turtle syntax. */
@Controller
public class OntologyController {

  private final CortexOntology cortex;

  /**
   * Creates the controller.
   *
   * @param cortex the ontology role used to render the ontology
   */
  public OntologyController(CortexOntology cortex) {
    this.cortex = cortex;
  }

  /**
   * Renders the ontology in Turtle syntax.
   *
   * @param model receives {@code ontology} (the ontology serialized in Turtle syntax)
   * @return the {@code ontology} view name
   */
  @GetMapping("/ontology")
  public String getOntology(Model model) {
    model.addAttribute("ontology", cortex.getOntology());
    return "ontology";
  }
}
