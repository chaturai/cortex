package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import java.io.IOException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * MCP tool that lets AI agents ingest assertions into the knowledge graph, staged on a branch for
 * human review.
 */
public class IngestTools {

  private final Cortex cortex;

  public IngestTools(Cortex cortex) {
    this.cortex = cortex;
  }

  @McpTool(
      description =
          "Ensure that input assertions are in text/turtle format and based on cortex://ontology",
      annotations =
          @McpTool.McpAnnotations(title = "Ingest", destructiveHint = false, openWorldHint = false))
  IngestResult ingest(
      @McpToolParam(description = "RDF Data to be ingested to knowledge graph in TTL syntax")
          String ttl)
      throws IOException {
    return cortex.ingest(ttl);
  }
}
