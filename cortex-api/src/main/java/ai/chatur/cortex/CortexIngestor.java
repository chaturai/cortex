package ai.chatur.cortex;

/** Validates and stages incoming RDF assertions onto a review branch. */
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
}
