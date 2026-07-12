package ai.chatur.cortex.spring.ontology;

import ai.chatur.cortex.Cortex;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Web UI page rendering the ontology in Turtle syntax. */
@Controller
public class OntologyController {

  private final Cortex cortex;

  public OntologyController(Cortex cortex) {
    this.cortex = cortex;
  }

  @GetMapping("/ontology")
  public String getOntology(Model model) throws IOException {
    model.addAttribute("ontology", cortex.getOntology());
    return "ontology";
  }
}
