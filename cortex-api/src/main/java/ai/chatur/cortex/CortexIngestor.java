package ai.chatur.cortex;

/**
 * Validates and stages incoming RDF assertions, and edits to approved ones, onto a review branch.
 */
public interface CortexIngestor {

  /**
   * Validates the given assertions and stages them on a new branch.
   *
   * <p>The input must first pass the {@link CortexLinter#lint(String) lint check} against the
   * ontology; assertions failing it are rejected without being staged. Input that lints clean is
   * then validated against the configured SHACL shapes, together with the already approved
   * assertions, so incoming statements may rely on approved ones to conform. If the union conforms,
   * the statements not already approved are stored on a newly created branch awaiting {@link
   * CortexBranches#approve(String) approval}; otherwise nothing is stored and the validation errors
   * are reported in the result. Statements that are already approved are never staged again; if
   * nothing novel remains, no branch is created.
   *
   * @param ttl RDF assertions in Turtle syntax, based on the classes and properties of {@link
   *     CortexOntology#getOntology() the ontology}
   * @return the outcome, carrying either the name of the created branch — {@code null} if every
   *     assertion was already approved — or the lint or validation errors
   */
  IngestResult ingest(String ttl);

  /**
   * Stages an edit to an already-approved resource on a new branch.
   *
   * <p>Editing an approved statement means retracting it as well as asserting its replacement, so
   * the branch this stages carries both: the statements to add and the statements to remove. It is
   * still only a proposal — nothing reaches the approved assertions until the branch is {@link
   * CortexBranches#approve(String) approved}, which is the same guarantee {@link #ingest(String)}
   * gives.
   *
   * <p>The additions must lint clean against the ontology, and the assertions <em>as they would be
   * once the branch is approved</em> must conform to the configured SHACL shapes — a stricter check
   * than {@link #ingest(String)} makes, because a retraction can break a constraint an approved
   * statement was satisfying. If either fails, nothing is staged and the errors are reported in the
   * result. Additions already approved and retractions of statements that are not approved are
   * dropped; if nothing remains, no branch is created.
   *
   * @param edit the proposed edit
   * @return the outcome, carrying either the name of the created branch — {@code null} if the edit
   *     would change nothing — or the lint or validation errors
   */
  IngestResult revise(ResourceEdit edit);
}
