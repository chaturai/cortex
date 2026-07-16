package ai.chatur.cortex;

/**
 * A statement about a resource together with its provenance, as returned by {@link
 * Cortex#describe(String)}.
 *
 * @param predicate the property of the statement
 * @param object the value of the statement
 * @param created when the statement was approved into the knowledge graph, or {@code null} for
 *     statements without recorded provenance (such as inferred statements)
 */
public record ProvenancedStatement(Term predicate, Term object, String created) {}
