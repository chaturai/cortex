package ai.chatur.cortex;

/**
 * The outcome of {@link Cortex#ingest(String) ingesting} assertions into the knowledge graph.
 *
 * @param valid whether the assertions pass the lint check against the ontology and conform to the
 *     configured SHACL shapes
 * @param branch the name of the branch the assertions were staged on, or {@code null} if validation
 *     failed
 * @param errors the lint violations or the SHACL validation report, or {@code null} if validation
 *     passed
 */
public record IngestResult(boolean valid, String branch, String errors) {}
