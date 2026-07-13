package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.Cortex;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Web UI for exploring the knowledge graph and reviewing staged branches: browse classes,
 * instances, and statements with provenance, and edit, approve, or reject pending branches.
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
    model.addAttribute(
        "branches", cortex.listBranches().stream().map(cortex::getBranchInfo).toList());
    return "branches";
  }

  @GetMapping("/branches/{branch}")
  public String getBranch(@PathVariable("branch") String branch, Model model) {
    model.addAttribute("branch", branch);
    model.addAttribute("subjects", cortex.getBranchSubjects(branch));
    return "branch";
  }

  @PostMapping("/branches/{branch}/update")
  @ResponseBody
  public ResponseEntity<Void> updateBranch(
      @PathVariable("branch") String branch, @RequestBody List<BranchChange> changes) {
    return cortex.updateBranch(branch, changes)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
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
