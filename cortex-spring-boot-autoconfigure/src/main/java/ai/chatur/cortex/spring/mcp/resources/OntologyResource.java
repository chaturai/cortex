package ai.chatur.cortex.spring.mcp.resources;

import ai.chatur.cortex.OntologyRepository;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OntologyResource {
  @Autowired OntologyRepository ontologyRepository;

  @McpResource(
      uri = "cortex://ontology",
      name = "Ontology",
      description = "Access Cortex Ontology in Turtle format")
  public McpSchema.ReadResourceResult getOntology(McpMeta meta) {
    String ontology = ontologyRepository.getOntology();
    Map<String, Object> ontologyMeta = Map.ofEntries(Map.entry("format", "Turtle"));

    McpSchema.TextResourceContents ontologyResource =
        McpSchema.TextResourceContents.builder("cortex://ontology", ontology)
            .mimeType("text/turtle")
            .meta(ontologyMeta)
            .build();

    return McpSchema.ReadResourceResult.builder(List.of(ontologyResource)).build();
  }
}
