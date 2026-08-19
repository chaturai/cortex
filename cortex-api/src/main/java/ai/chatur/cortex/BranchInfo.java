package ai.chatur.cortex;

/**
 * A summary of a branch pending review.
 *
 * <p>The two counts are kept apart because they mean opposite things to a reviewer: a branch that
 * only adds statements can be approved without reading what is already in the graph, and one that
 * removes any cannot.
 *
 * @param name the branch name
 * @param started when the branch was staged, or {@code null} if it carries no provenance activity
 * @param additions the number of statements the branch stages for addition, excluding the
 *     statements of its own provenance activity
 * @param retractions the number of statements the branch stages for removal
 */
public record BranchInfo(String name, String started, long additions, long retractions) {}
