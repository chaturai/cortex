package ai.chatur.cortex;

/**
 * Entry point to a Cortex knowledge graph.
 *
 * <p>Cortex is an ontology-backed memory store. Assertions are ingested as RDF, validated against
 * SHACL shapes, and staged on a <em>branch</em> (a named graph) until they are explicitly {@link
 * CortexBranches#approve(String) approved} into the graph or {@link CortexBranches#reject(String)
 * rejected}. Approved statements are recorded with provenance, enriched by rule-based inference,
 * and can be explored via SPARQL queries or full-text search.
 *
 * <p>This interface composes every role — {@link CortexOntology}, {@link CortexLinter}, {@link
 * CortexIngestor}, {@link CortexBranches}, {@link CortexArchive}, {@link CortexQuery}, {@link
 * CortexSearch}, {@link CortexStatistics}, and {@link CortexInference} — for convenience. Consumers
 * that only need a subset of this surface should depend on the corresponding role interface
 * directly rather than on {@code Cortex}, so their dependency reflects what they actually use.
 */
public interface Cortex
    extends CortexOntology,
        CortexLinter,
        CortexIngestor,
        CortexBranches,
        CortexArchive,
        CortexQuery,
        CortexSearch,
        CortexStatistics,
        CortexInference {}
