package ai.chatur.cortex;

/**
 * Summary of a branch pending review, drawn from the provenance activity recorded when the branch
 * was staged.
 *
 * @param name the branch name
 * @param started when the branch was staged, or {@code null} if no activity was recorded
 * @param size the number of assertion triples staged on the branch, excluding the activity
 */
public record BranchInfo(String name, String started, long size) {}
