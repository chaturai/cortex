package ai.chatur.cortex;

/**
 * A reviewer's rename of a subject staged on a branch, replacing its IRI in every staged statement
 * in which it appears, as subject or object.
 *
 * @param subject the current full subject IRI
 * @param newSubject the replacement IRI
 */
public record BranchRename(String subject, String newSubject) {}
