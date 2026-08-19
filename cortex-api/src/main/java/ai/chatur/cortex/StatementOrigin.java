package ai.chatur.cortex;

/**
 * Where a statement returned by {@link Cortex#describe(String)} came from.
 *
 * <p>The three origins are exhaustive and mutually exclusive: a statement is either something a
 * human approved, part of the ontology the graph is built on, or a consequence the reasoner drew
 * from the two.
 */
public enum StatementOrigin {

  /** Approved into the knowledge graph by a human, and therefore carrying provenance. */
  ASSERTED,

  /**
   * Part of the ontology the graph is built on. The reasoner is bound to the ontology as its
   * schema, so the ontology's own axioms are visible alongside the assertions; they were authored
   * with the application, not derived and not approved, so they carry no provenance.
   */
  ONTOLOGY,

  /** Derived by the reasoner from the approved assertions and the ontology. */
  INFERRED
}
