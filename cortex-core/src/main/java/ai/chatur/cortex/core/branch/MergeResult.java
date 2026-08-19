package ai.chatur.cortex.core.branch;

import org.apache.jena.rdf.model.Model;

/**
 * What approving a branch did to the knowledge graph.
 *
 * <p>{@code retracted} is what the caller needs to keep the inference closure honest: the closure
 * can be extended with newly approved statements incrementally, but it cannot be <em>shrunk</em>
 * that way, so a merge that removed anything has to be followed by a full recomputation.
 *
 * @param added the newly approved assertions, empty if every staged statement was already approved
 * @param retracted whether the merge removed any statement from the knowledge graph
 */
public record MergeResult(Model added, boolean retracted) {}
