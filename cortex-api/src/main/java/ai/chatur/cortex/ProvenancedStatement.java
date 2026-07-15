package ai.chatur.cortex;

/**
 * A statement about a resource together with its provenance, as returned by {@link
 * Cortex#describe(String)}.
 *
 * @param predicate the property of the statement
 * @param object the value of the statement
 * @param created when the statement was approved into the knowledge graph, or {@code null} for
 *     statements without recorded provenance (such as inferred statements)
 */
public record ProvenancedStatement(Term predicate, Term object, String created) {

  /**
   * One node of a statement.
   *
   * @param prefix the namespace prefix from the prefix mapping, or {@code null} if no prefix
   *     matches or the node is a literal
   * @param localName the part of the URI after the mapped namespace, the full URI if no prefix
   *     matches, or the lexical form for a literal
   * @param uri the full URI, or {@code null} for a literal
   */
  public record Term(String prefix, String localName, String uri) {}
}
