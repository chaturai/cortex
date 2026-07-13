package ai.chatur.cortex;

import java.util.List;

/**
 * The statements staged on a branch about one subject.
 *
 * @param name the local name of the subject, for display
 * @param uri the full subject IRI
 * @param statements the staged statements about the subject, sorted by predicate
 */
public record BranchSubject(String name, String uri, List<BranchStatement> statements) {}
