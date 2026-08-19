package ai.chatur.cortex;

/**
 * A reviewer's change to a single statement: a deletion, an edit replacing the object, or an
 * addition.
 *
 * <p>{@code object} and {@code newObject} between them say which of the three it is. {@code object}
 * names the statement as it stands and {@code newObject} the statement to put in its place, so a
 * {@code null} on either side means that side does not exist:
 *
 * <ul>
 *   <li>both non-{@code null} — replace the object of the statement,
 *   <li>{@code newObject == null} — delete the statement,
 *   <li>{@code object == null} — add the statement; there is nothing to replace.
 * </ul>
 *
 * <p>The same record describes an edit to a statement staged on a branch, applied by {@link
 * CortexBranches#updateBranch}, and a proposed edit to an already-approved statement, staged for
 * review by {@link CortexIngestor#revise}.
 *
 * @param subject the full subject IRI
 * @param predicate the full predicate IRI, or the prefixed short form when adding a statement
 * @param object the current object — the full IRI of a resource, or the lexical form of a literal —
 *     or {@code null} when adding a statement
 * @param literal whether the object is a literal
 * @param datatype the datatype IRI of a literal object, or {@code null} for a resource
 * @param language the language tag of a language-tagged literal object, or {@code null} for a
 *     resource or a literal without one
 * @param newObject the replacement object, or {@code null} to delete the statement
 * @param retracted whether the change addresses a statement the branch stages for removal rather
 *     than one it stages for addition; only meaningful for {@link CortexBranches#updateBranch}, and
 *     only as a deletion — a staged retraction can be cancelled but not edited
 */
public record BranchChange(
    String subject,
    String predicate,
    String object,
    boolean literal,
    String datatype,
    String language,
    String newObject,
    boolean retracted) {}
