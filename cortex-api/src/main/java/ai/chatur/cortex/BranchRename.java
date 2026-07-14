package ai.chatur.cortex;

/**
 * A reviewer's rename of a subject staged on a branch: statements referencing the IRI as object are
 * rewritten to the new IRI, and statements carrying it as subject are removed.
 *
 * @param subject the current full subject IRI
 * @param newSubject the replacement IRI
 */
public record BranchRename(String subject, String newSubject) {}
