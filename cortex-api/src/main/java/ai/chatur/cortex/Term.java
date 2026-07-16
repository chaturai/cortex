package ai.chatur.cortex;

/**
 * One node of a statement.
 *
 * @param prefix the namespace prefix from the prefix mapping, or {@code null} if no prefix matches
 *     or the node is a literal
 * @param localName the part of the URI after the mapped namespace, the full URI if no prefix
 *     matches, or the lexical form for a literal
 * @param uri the full URI, or {@code null} for a literal
 */
public record Term(String prefix, String localName, String uri) {}
