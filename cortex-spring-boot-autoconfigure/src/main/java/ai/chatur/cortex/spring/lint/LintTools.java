package ai.chatur.cortex.spring.lint;

import ai.chatur.cortex.CortexLinter;
import ai.chatur.cortex.LintResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * MCP tool that lets AI agents lint assertions against the ontology before ingesting them into the
 * knowledge graph.
 */
public class LintTools {

  private final CortexLinter cortex;

  /**
   * Creates the tool.
   *
   * @param cortex the linter role used to lint assertions
   */
  public LintTools(CortexLinter cortex) {
    this.cortex = cortex;
  }

  @McpTool(
      description =
          "Lint assertions in Turtle syntax against cortex://ontology. Always call this before the"
              + " Ingest tool and pass only the validated TTL it returns to Ingest. Classes and"
              + " properties not defined in the ontology are rejected; only rdf:type, rdfs:label,"
              + " and rdfs:comment are allowed beyond it. Returns the validated TTL, or the"
              + " violations to fix.",
      annotations =
          @McpTool.McpAnnotations(
              title = "Lint",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  LintResult lint(@McpToolParam(description = "RDF assertions to lint in TTL syntax") String ttl) {
    return cortex.lint(ttl);
  }
}
