package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Web UI for exploring the knowledge graph and reviewing staged branches: browse classes,
 * instances, and statements with provenance, and approve or reject pending branches.
 */
@Controller
public class IngestController {

  private final Cortex cortex;

  public IngestController(Cortex cortex) {
    this.cortex = cortex;
  }

  @GetMapping("/assertions")
  public String getAssertions(
      @RequestParam(value = "type", required = false) String type, Model model) {
    if (type == null) {
      model.addAttribute("classes", cortex.getClassHierarchy());
      return "classes";
    }
    model.addAttribute("type", type);
    model.addAttribute("instances", cortex.getInstances(type));
    return "instances";
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

  @GetMapping("/assertions/{*id}")
  public String describe(@PathVariable("id") String id, Model model) {
    String subject = id.startsWith("/") ? id.substring(1) : id;
    model.addAttribute("subject", subject);
    model.addAttribute("statements", cortex.describe(subject));
    return "describe";
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
