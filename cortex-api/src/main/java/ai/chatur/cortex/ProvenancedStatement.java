package ai.chatur.cortex;

/**
 * A statement about a resource together with its provenance, as returned by {@link
 * Cortex#describe(String)}.
 *
 * <p>{@code predicate} and {@code object} carry the display form; {@code literal}, {@code
 * datatype}, and {@code language} carry what {@link Term} cannot, so that a statement whose origin
 * is {@link StatementOrigin#ASSERTED} can be round-tripped back to RDF as a {@link BranchChange}.
 * They mirror {@link BranchStatement}, which exists for the same reason on the branch side.
 *
 * @param predicate the property of the statement
 * @param object the value of the statement
 * @param created when the statement was approved into the knowledge graph, or {@code null} for
 *     statements without recorded provenance (those whose origin is {@link
 *     StatementOrigin#ONTOLOGY} or {@link StatementOrigin#INFERRED})
 * @param origin where the statement came from
 * @param literal whether the object is a literal
 * @param datatype the datatype IRI of a literal object, or {@code null} for a resource
 * @param language the language tag of a language-tagged literal object, or {@code null} for a
 *     resource or a literal without one
 */
public record ProvenancedStatement(
    Term predicate,
    Term object,
    String created,
    StatementOrigin origin,
    boolean literal,
    String datatype,
    String language) {}
