package ai.chatur.cortex;

/**
 * A full-text search hit in the knowledge graph.
 *
 * @param subject the identifier of the matching resource within the Cortex namespace
 * @param match the indexed literal that matched the search text
 */
public record SearchResult(Term subject, String match) {}
