package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class IngestController {
  @Autowired Cortex cortex;

  @GetMapping("/")
  public RedirectView getIndex() {
    return new RedirectView("/ontology");
  }

  @GetMapping("/assertions")
  public String getAssertions(Model model) throws IOException {
    model.addAttribute("assertions", cortex.getAssertions());
    return "assertions";
  }

  @GetMapping("/branches")
  public String listBranches(Model model) {
    model.addAttribute("branches", cortex.listBranches());
    return "branches";
  }

  @GetMapping("/branches/{branch}")
  public String getBranch(@PathVariable("branch") String branch, Model model) throws IOException {
    model.addAttribute("assertions", cortex.getBranch(branch));
    return "assertions";
  }

  @GetMapping("/assertions/{id}")
  public String describe(@PathVariable("id") String id, Model model) throws IOException {
    model.addAttribute("assertions", cortex.describe(id));
    return "assertions";
  }

  @PostMapping("/branches/{branch}/approve")
  public RedirectView approveBranch(@PathVariable("branch") String branch) {
    cortex.approve(branch);
    return new RedirectView("/assertions");
  }

  @PostMapping("/branches/{branch}/reject")
  public RedirectView rejectBranch(@PathVariable("branch") String branch) {
    cortex.reject(branch);
    return new RedirectView("/branches");
  }
}
