package ai.chatur.cortex;

import java.util.List;

/** Looks up instances and resources in the knowledge graph, including SPARQL access. */
public interface CortexQuery {

  /**
   * Returns the known instances of an ontology class, including those derived by inference.
   *
   * @param type the URI of the ontology class
   * @return the instance identifiers sorted alphabetically, empty if the class is unknown
   */
  List<Term> getInstances(String type);

  /**
   * Returns everything known about a resource, including statements derived by inference.
   *
   * @param id the identifier of the resource, as returned by {@link #getInstances(String)}, or a
   *     full URI
   * @return the statements about the resource with their provenance, sorted by predicate
   */
  List<ProvenancedStatement> describe(String id);

  /**
   * Runs a SPARQL query against the knowledge graph, including statements derived by inference.
   *
   * @param sparql a SPARQL {@code SELECT}, {@code ASK}, or {@code DESCRIBE} query
   * @return {@code SELECT} and {@code ASK} results formatted as text, {@code DESCRIBE} results
   *     serialized in Turtle syntax, or {@code null} for other query types
   */
  String query(String sparql);
}
