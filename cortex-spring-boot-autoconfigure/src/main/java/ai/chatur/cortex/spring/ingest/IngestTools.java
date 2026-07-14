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
          "Ensure that input assertions are in text/turtle format and based on cortex://ontology."
              + " Always call the Lint tool first and ingest only the validated TTL it returns."
              + " Before generating new data, use the Search or Query tools to find out whether"
              + " the instances involved already exist in the knowledge graph, and reuse their"
              + " IRIs, so that the same instance is never ingested under multiple names."
              + " When the result contains a branch name, open the review page at"
              + " /branches/<branch> on this MCP server's host in a UI (e.g. the browser) so the"
              + " staged assertions can be reviewed and approved",
      annotations =
          @McpTool.McpAnnotations(title = "Ingest", destructiveHint = false, openWorldHint = false))
  IngestResult ingest(
      @McpToolParam(description = "RDF Data to be ingested to knowledge graph in TTL syntax")
          String ttl)
      throws IOException {
    return cortex.ingest(ttl);
  }
}
