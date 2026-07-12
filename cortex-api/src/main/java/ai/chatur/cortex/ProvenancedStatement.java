package ai.chatur.cortex;

/**
 * A statement about a resource together with its provenance, as returned by {@link
 * Cortex#describe(String)}.
 *
 * @param predicate the property of the statement, in prefixed short form where possible
 * @param object the value of the statement, in prefixed short form where possible
 * @param created when the statement was approved into the knowledge graph, or {@code null} for
 *     statements without recorded provenance (such as inferred statements)
 */
public record ProvenancedStatement(String predicate, String object, String created) {}
