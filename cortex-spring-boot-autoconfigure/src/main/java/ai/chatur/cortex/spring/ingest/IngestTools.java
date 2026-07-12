package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.IngestService;
import java.io.IOException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IngestTools {
  @Autowired IngestService ingestService;

  @McpTool
  IngestResult ingest(String ttl) throws IOException {
    return ingestService.ingest(ttl);
  }
}
