package ai.chatur.cortex;

/**
 * A reviewer's change to a statement staged on a branch: a deletion, or an edit replacing the
 * object.
 *
 * @param subject the full subject IRI
 * @param predicate the full predicate IRI
 * @param object the current object: the full IRI of a resource, or the lexical form of a literal
 * @param literal whether the object is a literal
 * @param datatype the datatype IRI of a literal object, or {@code null} for a resource
 * @param newObject the replacement object, or {@code null} to delete the statement
 */
public record BranchChange(
    String subject,
    String predicate,
    String object,
    boolean literal,
    String datatype,
    String newObject) {}
