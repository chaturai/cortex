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
   * Lints the given assertions against {@link #getOntology() the ontology}.
   *
   * <p>Every property used must be declared in the ontology, and every {@code rdf:type} object must
   * be a class declared in the ontology. The only terms permitted beyond the ontology are {@code
   * rdf:type}, {@code rdfs:label}, and {@code rdfs:comment}. Call this before {@link
   * #ingest(String)} and ingest only the validated Turtle it returns.
   *
   * @param ttl RDF assertions in Turtle syntax
   * @return the outcome, carrying either the validated assertions in Turtle syntax or the lint
   *     violations
   * @throws IOException if the validated assertions cannot be serialized
   */
  LintResult lint(String ttl) throws IOException;

  /**
   * Validates the given assertions and stages them on a new branch.
   *
   * <p>The input must first pass the {@link #lint(String) lint check} against the ontology;
   * assertions failing it are rejected without being staged. Input that lints clean is then
   * validated against the configured SHACL shapes, together with the already approved assertions,
   * so incoming statements may rely on approved ones to conform. If the union conforms, the
   * statements not already approved are stored on a newly created branch awaiting {@link
   * #approve(String) approval}; otherwise nothing is stored and the validation errors are reported
   * in the result. Statements that are already approved are never staged again; if nothing novel
   * remains, no branch is created.
   *
   * @param ttl RDF assertions in Turtle syntax, based on the classes and properties of {@link
   *     #getOntology() the ontology}
   * @return the outcome, carrying either the name of the created branch — {@code null} if every
   *     assertion was already approved — or the lint or validation errors
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
   * Summarizes a branch pending review from the provenance activity recorded when it was staged.
   *
   * @param branch the branch name
   * @return the branch summary
   */
  BranchInfo getBranchInfo(String branch);

  /**
   * Returns the assertions staged on the given branch grouped by subject, excluding the provenance
   * activity of the ingestion.
   *
   * @param branch the branch name
   * @return the staged subjects sorted by name, each with its statements sorted by predicate
   */
  List<BranchSubject> getBranchSubjects(String branch);

  /**
   * Applies reviewer changes — deletions and object edits — to the assertions staged on the given
   * branch.
   *
   * <p>When the deletions remove every statement a subject carried, its IRI is gone from the
   * branch, so every staged statement referencing that IRI as object is deleted as well.
   *
   * <p>Changes addressing the provenance activity of the branch are ignored.
   *
   * @param branch the branch name
   * @param changes the changes to apply
   * @return {@code true} if the branch existed and the changes were applied
   */
  boolean updateBranch(String branch, List<BranchChange> changes);

  /**
   * Renames subjects staged on the given branch.
   *
   * <p>Every staged statement referencing a renamed IRI as object is rewritten to reference the new
   * IRI; the statements describing the renamed subject — those carrying the IRI as subject — are
   * removed rather than rewritten.
   *
   * <p>Renames addressing the provenance activity of the branch are ignored.
   *
   * @param branch the branch name
   * @param renames the renames to apply
   * @return {@code true} if the branch existed and the renames were applied
   */
  boolean renameBranchSubjects(String branch, List<BranchRename> renames);

  /**
   * Merges the assertions staged on the given branch into the knowledge graph.
   *
   * <p>Each merged statement is recorded with creation provenance, the branch is deleted, and the
   * newly approved statements are added to the inference closure incrementally. Does nothing if the
   * branch does not exist.
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
   * Exports the entire assertions dataset — the approved assertions and every staged branch — for
   * backup.
   *
   * @return the dataset serialized in TriG syntax
   * @throws IOException if the dataset cannot be serialized
   */
  String exportAssertions() throws IOException;

  /**
   * Restores the assertions dataset from an {@link #exportAssertions() exported backup}, replacing
   * the approved assertions and every staged branch, and recomputes inference.
   *
   * <p>If the backup cannot be parsed, the dataset is left untouched.
   *
   * @param trig the backup, a dataset serialized in TriG syntax
   */
  void importAssertions(String trig);

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
   * Searches the knowledge graph by free text and returns the matching subjects.
   *
   * <p>Each term of the input is matched approximately, so small typos and spelling variations
   * still find their target.
   *
   * @param text the text to search for
   * @return the matching subjects ranked best first, empty if nothing matches
   */
  List<SearchResult> searchSubjects(String text);

  /**
   * Returns a snapshot of the size and activity of the knowledge graph.
   *
   * @return the current statistics
   */
  CortexStats getStats();

  /**
   * Rebuilds the statements derived by inference from the current assertions.
   *
   * <p>Invoked automatically at startup and after {@link #importAssertions(String)}; {@link
   * #approve(String)} extends the closure incrementally instead. Call this directly only when the
   * underlying assertions have been modified through other means.
   */
  void recomputeInference();
}
