package ai.chatur.cortex.spring.ontology;

import ai.chatur.cortex.Cortex;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpResource;

/**
 * MCP resource serving the ontology at {@code cortex://ontology}, so AI agents can ground the
 * assertions they produce in the vocabulary of the knowledge graph.
 */
public class OntologyResources {

  private final Cortex cortex;

  public OntologyResources(Cortex cortex) {
    this.cortex = cortex;
  }

  @McpResource(
      uri = "cortex://ontology",
      name = "Ontology",
      description = "Access Cortex Ontology in Turtle format")
  public McpSchema.ReadResourceResult getOntology(McpMeta meta) throws IOException {
    String ontology = cortex.getOntology();
    Map<String, Object> ontologyMeta = Map.ofEntries(Map.entry("format", "Turtle"));

    McpSchema.TextResourceContents ontologyResource =
        McpSchema.TextResourceContents.builder("cortex://ontology", ontology)
            .mimeType("text/turtle")
            .meta(ontologyMeta)
            .build();

    return McpSchema.ReadResourceResult.builder(List.of(ontologyResource)).build();
  }
}
