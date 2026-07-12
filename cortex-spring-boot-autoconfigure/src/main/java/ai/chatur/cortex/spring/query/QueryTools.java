package ai.chatur.cortex.spring.query;

import ai.chatur.cortex.Cortex;
import java.io.IOException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

public class QueryTools {

  private final Cortex cortex;

  public QueryTools(Cortex cortex) {
    this.cortex = cortex;
  }

  @McpTool(
      description =
          "Use this tool only to SELECT or ASK questions. Refer to cortex://ontology to get ontology definitions",
      annotations =
          @McpTool.McpAnnotations(
              title = "Query",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  String query(@McpToolParam(description = "SPARQL SELECT or ASK query") String sparql)
      throws IOException {
    return cortex.query(sparql);
  }

  @McpTool(
      description =
          "Use this tool to find resources by full-text search over their labels. Returns matches ranked by relevance",
      annotations =
          @McpTool.McpAnnotations(
              title = "Search",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  String search(@McpToolParam(description = "Text to search for") String text) {
    return cortex.search(text);
  }
}
