package ai.chatur.cortex;

/** Validates RDF assertions against the ontology before they are ingested. */
public interface CortexLinter {

  /**
   * Lints the given assertions against {@link CortexOntology#getOntology() the ontology}.
   *
   * <p>Every property used must be declared in the ontology, and every {@code rdf:type} object must
   * be a class declared in the ontology. The only terms permitted beyond the ontology are {@code
   * rdf:type}, {@code rdfs:label}, and {@code rdfs:comment}.
   *
   * <p>{@link CortexIngestor#ingest(String) ingest} performs this same lint check internally — and
   * the subsequent SHACL validation, which this method does not — so calling this first is not
   * required for correctness; it exists to let a caller (an AI agent, in particular) catch and fix
   * ontology violations before attempting a full ingestion. Note that {@code ingest} re-parses the
   * caller's original Turtle rather than consuming {@link LintResult#ttl() the validated Turtle
   * this returns}, so passing anything other than that exact string to {@code ingest} afterward is
   * equally valid.
   *
   * @param ttl RDF assertions in Turtle syntax
   * @return the outcome, carrying either the validated assertions in Turtle syntax or the lint
   *     violations
   */
  LintResult lint(String ttl);
}
