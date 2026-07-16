package ai.chatur.cortex.spring.query;

import ai.chatur.cortex.CortexQuery;
import ai.chatur.cortex.CortexSearch;
import java.util.function.Predicate;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * MCP tools that let AI agents interrogate the knowledge graph with SPARQL queries and full-text
 * search.
 */
public class QueryTools {

  private static final Logger log = LoggerFactory.getLogger(QueryTools.class);

  private final CortexQuery cortexQuery;
  private final CortexSearch cortexSearch;

  /**
   * Creates the tools.
   *
   * @param cortexQuery the query role used to run SPARQL queries
   * @param cortexSearch the search role used for full-text search
   */
  public QueryTools(CortexQuery cortexQuery, CortexSearch cortexSearch) {
    this.cortexQuery = cortexQuery;
    this.cortexSearch = cortexSearch;
  }

  @McpTool(
      description =
          "Use this tool to run a SPARQL SELECT query. Returns the results as a text table. Refer to cortex://ontology to get ontology definitions",
      annotations =
          @McpTool.McpAnnotations(
              title = "Query",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  String query(@McpToolParam(description = "SPARQL SELECT query") String sparql) {
    return execute(sparql, Query::isSelectType, "SELECT");
  }

  @McpTool(
      description =
          "Use this tool to answer a yes/no question with a SPARQL ASK query. Returns true or false. Refer to cortex://ontology to get ontology definitions",
      annotations =
          @McpTool.McpAnnotations(
              title = "Ask",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  String ask(@McpToolParam(description = "SPARQL ASK query") String sparql) {
    return execute(sparql, Query::isAskType, "ASK");
  }

  @McpTool(
      description =
          "Use this tool to fetch everything known about resources with a SPARQL DESCRIBE query. Returns the results in Turtle syntax. Refer to cortex://ontology to get ontology definitions",
      annotations =
          @McpTool.McpAnnotations(
              title = "Describe",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  String describe(@McpToolParam(description = "SPARQL DESCRIBE query") String sparql) {
    return execute(sparql, Query::isDescribeType, "DESCRIBE");
  }

  @McpTool(
      description =
          "Use this tool to find resources by fuzzy full-text search over their labels, tolerating small typos and spelling variations. Returns matches ranked by relevance",
      annotations =
          @McpTool.McpAnnotations(
              title = "Search",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  String search(@McpToolParam(description = "Text to search for") String text) {
    return cortexSearch.search(text);
  }

  String execute(String sparql, Predicate<Query> expectedType, String expected) {
    Query query;
    try {
      query = QueryFactory.create(sparql);
    } catch (QueryParseException e) {
      log.warn("Rejected malformed SPARQL query: {}", e.getMessage());
      throw e;
    }
    if (!expectedType.test(query)) {
      log.warn("Rejected SPARQL query that is not a {} query", expected);
      throw new IllegalArgumentException("This tool only accepts SPARQL " + expected + " queries");
    }
    return cortexQuery.query(sparql);
  }
}
