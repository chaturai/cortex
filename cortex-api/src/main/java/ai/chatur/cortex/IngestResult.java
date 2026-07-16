package ai.chatur.cortex;

/**
 * The outcome of {@link Cortex#ingest(String) ingesting} assertions into the knowledge graph.
 *
 * <p>Exactly three combinations of the two nullable components occur: {@code valid=true,
 * branch!=null} when at least one novel statement was staged for review; {@code valid=true,
 * branch=null} when validation passed but every assertion already existed in the graph, so nothing
 * was staged; and {@code valid=false, errors!=null} when validation failed. {@code branch == null}
 * is therefore ambiguous on its own between "validation failed" and "nothing novel to stage" —
 * always read it together with {@code valid}. No type here enforces this; a sealed interface with
 * one case per outcome would, at the cost of a larger API surface for callers to switch over.
 *
 * @param valid whether the assertions pass the lint check against the ontology and conform to the
 *     configured SHACL shapes
 * @param branch the name of the branch the assertions were staged on, or {@code null} if validation
 *     failed or every assertion was already approved and nothing remained to stage
 * @param errors the lint violations or the SHACL validation report, or {@code null} if validation
 *     passed
 */
public record IngestResult(boolean valid, String branch, String errors) {}
