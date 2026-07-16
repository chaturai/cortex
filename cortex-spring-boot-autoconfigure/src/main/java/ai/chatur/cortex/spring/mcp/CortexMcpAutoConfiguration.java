package ai.chatur.cortex.spring.mcp;

import ai.chatur.cortex.CortexIngestor;
import ai.chatur.cortex.CortexLinter;
import ai.chatur.cortex.CortexOntology;
import ai.chatur.cortex.CortexQuery;
import ai.chatur.cortex.CortexSearch;
import ai.chatur.cortex.spring.CortexAutoConfiguration;
import ai.chatur.cortex.spring.ingest.IngestTools;
import ai.chatur.cortex.spring.lint.LintTools;
import ai.chatur.cortex.spring.ontology.OntologyResources;
import ai.chatur.cortex.spring.query.QueryTools;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * MCP server auto-configuration for Cortex: the tools and resources that let AI agents lint,
 * ingest, query, and search the knowledge graph, and read its ontology.
 *
 * <p>Runs {@link AutoConfiguration#after() after} {@link CortexAutoConfiguration}, so every MCP
 * bean here can depend on the role interfaces the core beans satisfy.
 *
 * <p>Active only when {@code cortex.mcp.enabled} is not set to {@code false} (defaults to enabled)
 * and Spring AI's MCP annotation support ({@link McpTool @McpTool}) is on the classpath — so a
 * consumer wanting the graph without an MCP server gets there with one property. Each bean is also
 * {@link ConditionalOnMissingBean @ConditionalOnMissingBean}, so a consumer may substitute their
 * own by declaring a bean of the same type.
 */
@AutoConfiguration(after = CortexAutoConfiguration.class)
@ConditionalOnProperty(prefix = "cortex.mcp", name = "enabled", matchIfMissing = true)
@ConditionalOnClass(McpTool.class)
public class CortexMcpAutoConfiguration {

  /** Creates the auto-configuration. Spring instantiates this; consumers do not. */
  public CortexMcpAutoConfiguration() {}

  /**
   * Creates the MCP tool letting AI agents lint assertions against the ontology before ingesting
   * them into the knowledge graph.
   *
   * @param cortex the linter role used to lint assertions
   * @return the lint MCP tool
   */
  @Bean
  @ConditionalOnMissingBean
  LintTools lintTools(CortexLinter cortex) {
    return new LintTools(cortex);
  }

  /**
   * Creates the MCP tool letting AI agents ingest assertions into the knowledge graph.
   *
   * @param cortex the ingestor role used to ingest assertions
   * @return the ingest MCP tool
   */
  @Bean
  @ConditionalOnMissingBean
  IngestTools ingestTools(CortexIngestor cortex) {
    return new IngestTools(cortex);
  }

  /**
   * Creates the MCP tools letting AI agents run SPARQL queries and full-text search.
   *
   * @param query the query role used to run SPARQL queries
   * @param search the search role used to run full-text search
   * @return the query and search MCP tools
   */
  @Bean
  @ConditionalOnMissingBean
  QueryTools queryTools(CortexQuery query, CortexSearch search) {
    return new QueryTools(query, search);
  }

  /**
   * Creates the MCP resource that serves the ontology to AI agents.
   *
   * @param cortex the ontology role used to read the ontology
   * @return the ontology MCP resource
   */
  @Bean
  @ConditionalOnMissingBean
  OntologyResources ontologyResources(CortexOntology cortex) {
    return new OntologyResources(cortex);
  }
}
