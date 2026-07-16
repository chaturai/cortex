package ai.chatur.cortex.spring;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for Cortex, bound to the {@code cortex.*} prefix.
 *
 * @param persistent whether the approved assertions are persisted to a TDB2 store on disk; when
 *     {@code false} (the default) they are held in memory. Either way, the inference closure and
 *     the full-text index built over it are always an in-memory cache, rebuilt from the assertions
 *     on every startup.
 * @param assertionsLocation the directory of the TDB2 store for assertions, used when persistent
 * @param ontologies the ontologies the knowledge graph is built on, in Turtle syntax, merged in
 *     order
 * @param shapes the SHACL shapes ingested assertions are validated against, in Turtle syntax,
 *     merged in order
 * @param rules the Jena rules used for inference, concatenated in order
 * @param web the web UI settings
 * @param mcp the MCP server settings
 */
@ConfigurationProperties(prefix = "cortex")
public record CortexProperties(
    @DefaultValue("false") boolean persistent,
    @DefaultValue(".cortex/db") String assertionsLocation,
    @DefaultValue("classpath:ontology.ttl") List<Resource> ontologies,
    @DefaultValue("classpath:ontology.shapes") List<Resource> shapes,
    @DefaultValue("classpath:ontology.rules") List<Resource> rules,
    @DefaultValue Web web,
    @DefaultValue Mcp mcp) {

  /**
   * Settings for the web UI auto-configured by {@link
   * ai.chatur.cortex.spring.web.CortexWebAutoConfiguration}.
   *
   * @param enabled whether the web UI controllers are registered; disabling this is how a consumer
   *     gets the knowledge graph without the UI, without excluding individual controller beans
   */
  public record Web(@DefaultValue("true") boolean enabled) {}

  /**
   * Settings for the MCP server auto-configured by {@link
   * ai.chatur.cortex.spring.mcp.CortexMcpAutoConfiguration}.
   *
   * @param enabled whether the MCP tools and resources are registered; disabling this is how a
   *     consumer gets the knowledge graph without an MCP server, without excluding individual tool
   *     beans
   */
  public record Mcp(@DefaultValue("true") boolean enabled) {}
}
