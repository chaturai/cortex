package ai.chatur.cortex.spring.graph;

import ai.chatur.cortex.CortexOntology;
import ai.chatur.cortex.CortexQuery;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Web UI for browsing the knowledge graph: the class hierarchy, the instances of a class, and
 * everything known about a single resource.
 */
@Controller
public class GraphController {

  private final CortexOntology ontology;
  private final CortexQuery query;

  /**
   * Creates the controller.
   *
   * @param ontology the ontology role used to render the class hierarchy
   * @param query the query role used to list instances and describe a resource
   */
  public GraphController(CortexOntology ontology, CortexQuery query) {
    this.ontology = ontology;
    this.query = query;
  }

  /**
   * Renders everything known about a single resource, including statements derived by inference.
   *
   * @param uri the identifier of the resource, as returned by {@link #getAssertions(String,
   *     Model)}, or a full URI
   * @param model receives {@code subject} (the requested identifier) and {@code statements} (the
   *     {@link ai.chatur.cortex.ProvenancedStatement}s describing it)
   * @return the {@code describe} view name
   */
  @GetMapping(value = "/describe", params = "uri")
  public String describeUri(@RequestParam("uri") String uri, Model model) {
    model.addAttribute("subject", uri);
    model.addAttribute("name", getName(uri));
    model.addAttribute("statements", query.describe(uri));
    return "describe";
  }

  /**
   * Returns the part of an IRI a reader would call the resource's name — everything after the last
   * separator.
   *
   * <p>The page renames a resource by substituting this back into the IRI it came from, so it has
   * to be a genuine suffix of {@code uri}: an abbreviation resolved against the ontology's prefixes
   * would not be, and renaming would then rewrite the wrong part of the IRI.
   *
   * @param uri the resource's IRI
   * @return the name, or the whole IRI if it carries no separator
   */
  String getName(String uri) {
    int separator =
        Math.max(uri.lastIndexOf('#'), Math.max(uri.lastIndexOf('/'), uri.lastIndexOf(':')));
    return separator < 0 ? uri : uri.substring(separator + 1);
  }

  /**
   * Renders the ontology's class hierarchy, or the instances of a single class when {@code type} is
   * given.
   *
   * @param type the URI of the ontology class whose instances to list, or {@code null} to render
   *     the class hierarchy
   * @param model when {@code type} is {@code null}, receives {@code classes} (the {@link
   *     ai.chatur.cortex.OntologyClass} hierarchy) and {@code counts} (the instance count keyed by
   *     class URI); otherwise receives {@code type} and {@code instances} (the matching {@link
   *     ai.chatur.cortex.Term}s)
   * @return the {@code classes} view name when {@code type} is {@code null}, otherwise the {@code
   *     instances} view name
   */
  @GetMapping("/assertions")
  public String getAssertions(
      @RequestParam(value = "type", required = false) String type, Model model) {
    if (type == null) {
      model.addAttribute("classes", ontology.getClassHierarchy());
      model.addAttribute("counts", query.countInstances());
      return "classes";
    }
    model.addAttribute("type", type);
    model.addAttribute("instances", query.getInstances(type));
    return "instances";
  }
}
