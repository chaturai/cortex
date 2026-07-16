package ai.chatur.cortex.spring.ontology;

import ai.chatur.cortex.CortexOntology;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpResource;

/**
 * MCP resource serving the ontology at {@code cortex://ontology}, so AI agents can ground the
 * assertions they produce in the vocabulary of the knowledge graph.
 */
public class OntologyResources {

  private final CortexOntology cortex;

  /**
   * Creates the resource.
   *
   * @param cortex the ontology role used to render the ontology
   */
  public OntologyResources(CortexOntology cortex) {
    this.cortex = cortex;
  }

  /**
   * Returns the ontology in Turtle syntax as an MCP resource.
   *
   * @param meta MCP request metadata, unused
   * @return the ontology resource contents, serialized in Turtle syntax
   */
  @McpResource(
      uri = "cortex://ontology",
      name = "Ontology",
      description = "Access Cortex Ontology in Turtle format")
  public McpSchema.ReadResourceResult getOntology(McpMeta meta) {
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
