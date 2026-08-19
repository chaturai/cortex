package ai.chatur.cortex;

/**
 * A statement staged on a branch, carrying the raw node values needed to round-trip {@link
 * BranchChange edits} back to the staged graph.
 *
 * @param predicate the predicate in prefixed short form, for display
 * @param predicateUri the full predicate IRI
 * @param object the object: the full IRI of a resource, or the lexical form of a literal
 * @param literal whether the object is a literal
 * @param datatype the datatype IRI of a literal object, or {@code null} for a resource
 * @param language the language tag of a language-tagged literal object, or {@code null} for a
 *     resource or a literal without one
 * @param retracted whether the branch stages this statement for <em>removal</em> from the approved
 *     assertions rather than for addition to them
 */
public record BranchStatement(
    String predicate,
    String predicateUri,
    String object,
    boolean literal,
    String datatype,
    String language,
    boolean retracted) {}
