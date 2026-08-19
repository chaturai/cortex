package ai.chatur.cortex;

/**
 * A reviewer's rename of a subject staged on a branch: every staged statement in which the IRI
 * appears — as subject or as object — is rewritten to use the new IRI.
 *
 * @param subject the current full subject IRI
 * @param newSubject the replacement IRI
 */
public record BranchRename(String subject, String newSubject) {}
