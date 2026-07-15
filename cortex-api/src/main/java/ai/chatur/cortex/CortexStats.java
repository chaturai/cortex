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
 * @param inferenceTriples the total number of triples visible to queries, including statements
 *     derived by inference
 * @param ontologyClasses the number of classes defined in the ontology
 * @param shapes the number of SHACL shapes ingested assertions are validated against
 * @param rules the number of rules used for inference
 */
public record CortexStats(
    long triplesAddedToday,
    long pendingBranches,
    long assertionTriples,
    long inferenceTriples,
    long ontologyClasses,
    long shapes,
    long rules) {}
