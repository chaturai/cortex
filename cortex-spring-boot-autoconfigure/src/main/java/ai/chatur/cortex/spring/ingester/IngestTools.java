package ai.chatur.cortex.spring.ingester;

import ai.chatur.cortex.IngestService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IngestTools {
  @Autowired IngestService ingestService;

  @McpTool
  void ingest(String ttl) {
    ingestService.ingest(ttl);
  }
}
