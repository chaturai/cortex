package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.IngestService;
import java.io.IOException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IngestTools {
  @Autowired IngestService ingestService;

  @McpTool(
      description =
          "Ensure that input assertions are in text/turtle format and based on cortex://ontology",
      annotations =
          @McpTool.McpAnnotations(
              title = "Ingest",
              readOnlyHint = false,
              destructiveHint = false,
              idempotentHint = false,
              openWorldHint = false))
  IngestResult ingest(
      @McpToolParam(
              required = true,
              description = "RDF Data to be ingested to knowledge graph in TTL syntax")
          String ttl)
      throws IOException {
    return ingestService.ingest(ttl);
  }
}
