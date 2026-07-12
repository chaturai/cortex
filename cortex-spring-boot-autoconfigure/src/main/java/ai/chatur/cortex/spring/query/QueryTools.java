package ai.chatur.cortex.spring.query;

import ai.chatur.cortex.Cortex;
import java.io.IOException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QueryTools {
  @Autowired Cortex cortex;

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
}
