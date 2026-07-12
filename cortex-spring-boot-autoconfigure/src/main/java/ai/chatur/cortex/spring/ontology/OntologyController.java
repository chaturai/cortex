package ai.chatur.cortex.spring.ontology;

import ai.chatur.cortex.Cortex;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OntologyController {
  @Autowired Cortex cortex;

  @GetMapping("/ontology")
  public String getOntology(Model model) throws IOException {
    model.addAttribute("ontology", cortex.getOntology());
    return "ontology";
  }
}
