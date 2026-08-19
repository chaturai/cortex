package ai.chatur.cortex;

/**
 * A snapshot of the size and activity of the knowledge graph, as returned by {@link
 * Cortex#getStats()}.
 *
 * @param triplesAddedToday the number of triples approved into the knowledge graph today, according
 *     to their recorded provenance
 * @param pendingBranches the number of branches with staged assertions awaiting review
 * @param assertionTriples the total number of triples in the approved assertions, excluding
 *     provenance triples, which are kept in a separate graph
 * @param inferredTriples the number of triples visible to queries that were derived by the reasoner
 *     — the inference dataset less the approved assertions and less the ontology's own axioms,
 *     which the reasoner is bound to as its schema and which are materialized alongside its
 *     conclusions
 * @param ontologyClasses the number of classes defined in the ontology
 * @param shapes the number of root (targeted) SHACL shapes ingested assertions are validated
 *     against — shapes reachable only as a nested {@code sh:property} of another shape are not
 *     counted separately, since they have no target of their own
 * @param rules the number of rules used for inference
 */
public record CortexStats(
    long triplesAddedToday,
    long pendingBranches,
    long assertionTriples,
    long inferredTriples,
    long ontologyClasses,
    long shapes,
    long rules) {}
