package ai.chatur.cortex;

/**
 * A full-text search hit in the knowledge graph.
 *
 * <p>Hits are reported once per resource, ranked by descending {@code score}. The score is the
 * relevance the text index assigned to the best-matching literal; it is comparable only within a
 * single result list, not across searches.
 *
 * @param subject the identifier of the matching resource within the Cortex namespace
 * @param match the indexed literal that matched the search text
 * @param score the relevance of the match, higher being more relevant
 */
public record SearchResult(Term subject, String match, double score) {}
