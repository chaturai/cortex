package ai.chatur.cortex;

import java.io.IOException;
import java.util.List;

/**
 * Entry point to a Cortex knowledge graph.
 *
 * <p>Cortex is an ontology-backed memory store. Assertions are ingested as RDF, validated against
 * SHACL shapes, and staged on a <em>branch</em> (a named graph) until they are explicitly {@link
 * #approve(String) approved} into the graph or {@link #reject(String) rejected}. Approved
 * statements are recorded with provenance, enriched by rule-based inference, and can be explored
 * via SPARQL queries or full-text search.
 */
public interface Cortex {

  /**
   * Returns the ontology that this knowledge graph is built on.
   *
   * @return the ontology serialized in Turtle syntax
   * @throws IOException if the ontology cannot be serialized
   */
  String getOntology() throws IOException;

  /**
   * Validates the given assertions and stages them on a new branch.
   *
   * <p>The input is validated against the configured SHACL shapes. If it conforms, the statements
   * are stored on a newly created branch awaiting {@link #approve(String) approval}; otherwise
   * nothing is stored and the validation errors are reported in the result.
   *
   * @param ttl RDF assertions in Turtle syntax, based on the classes and properties of {@link
   *     #getOntology() the ontology}
   * @return the outcome, carrying either the name of the created branch or the validation errors
   * @throws IOException if the input cannot be read or the validation report cannot be rendered
   */
  IngestResult ingest(String ttl) throws IOException;

  /**
   * Returns the names of all branches with staged assertions awaiting review.
   *
   * @return the branch names, empty if nothing is pending
   */
  List<String> listBranches();

  /**
   * Reports whether a branch with the given name is pending review.
   *
   * @param branch the branch name
   * @return {@code true} if the branch exists
   */
  boolean hasBranch(String branch);

  /**
   * Returns the assertions staged on the given branch.
   *
   * @param branch the branch name
   * @return the staged assertions serialized in Turtle syntax
   * @throws IOException if the assertions cannot be serialized
   */
  String getBranch(String branch) throws IOException;

  /**
   * Merges the assertions staged on the given branch into the knowledge graph.
   *
   * <p>Each merged statement is recorded with creation provenance, the branch is deleted, and
   * inference is recomputed. Does nothing if the branch does not exist.
   *
   * @param branch the branch name
   */
  void approve(String branch);

  /**
   * Discards the assertions staged on the given branch.
   *
   * <p>Does nothing if the branch does not exist.
   *
   * @param branch the branch name
   */
  void reject(String branch);

  /**
   * Returns all approved assertions in the knowledge graph.
   *
   * @return the assertions serialized in TriG syntax
   * @throws IOException if the assertions cannot be serialized
   */
  String getAssertions() throws IOException;

  /**
   * Returns the class hierarchy of the ontology.
   *
   * @return the root classes, each carrying its subclasses, sorted by name
   */
  List<OntologyClass> getClassHierarchy();

  /**
   * Returns the known instances of an ontology class, including those derived by inference.
   *
   * @param type the local name of the ontology class
   * @return the instance identifiers sorted alphabetically, empty if the class is unknown
   */
  List<String> getInstances(String type);

  /**
   * Returns everything known about a resource, including statements derived by inference.
   *
   * @param id the identifier of the resource, as returned by {@link #getInstances(String)}
   * @return the statements about the resource with their provenance, sorted by predicate
   */
  List<ProvenancedStatement> describe(String id);

  /**
   * Runs a SPARQL query against the knowledge graph, including statements derived by inference.
   *
   * @param sparql a SPARQL {@code SELECT}, {@code ASK}, or {@code DESCRIBE} query
   * @return {@code SELECT} and {@code ASK} results formatted as text, {@code DESCRIBE} results
   *     serialized in Turtle syntax, or {@code null} for other query types
   * @throws IOException if the query results cannot be rendered
   */
  String query(String sparql) throws IOException;

  /**
   * Finds resources by fuzzy full-text search over their labels.
   *
   * <p>Each term of the input is matched approximately, so small typos and spelling variations
   * still find their target.
   *
   * @param text the text to search for
   * @return the matches with their relevance scores, formatted as text and ranked best first
   */
  String search(String text);

  /**
   * Returns a snapshot of the size and activity of the knowledge graph.
   *
   * @return the current statistics
   */
  CortexStats getStats();

  /**
   * Rebuilds the statements derived by inference from the current assertions.
   *
   * <p>Invoked automatically after every {@link #approve(String)}; call it directly only when the
   * underlying assertions have been modified through other means.
   */
  void recomputeInference();
}
