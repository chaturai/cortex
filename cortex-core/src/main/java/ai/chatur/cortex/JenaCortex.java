package ai.chatur.cortex;

import ai.chatur.cortex.core.inference.InferenceService;
import ai.chatur.cortex.core.ingest.IngestService;
import ai.chatur.cortex.core.ontology.OntologyService;
import ai.chatur.cortex.core.query.QueryService;
import ai.chatur.cortex.core.stats.StatsService;
import java.io.IOException;
import java.util.List;

/**
 * {@link Cortex} implementation backed by <a href="https://jena.apache.org">Apache Jena</a>.
 *
 * <p>Composes the core services: {@link OntologyService} for the ontology and its class hierarchy,
 * {@link IngestService} for validated, branch-based ingestion with provenance, {@link
 * InferenceService} for rule-based inference, {@link QueryService} for SPARQL queries and full-text
 * search, and {@link StatsService} for statistics. Approving a branch automatically recomputes
 * inference.
 */
public class JenaCortex implements Cortex {

  private final OntologyService ontologyService;
  private final IngestService ingestService;
  private final InferenceService inferenceService;
  private final QueryService queryService;
  private final StatsService statsService;

  public JenaCortex(
      OntologyService ontologyService,
      IngestService ingestService,
      InferenceService inferenceService,
      QueryService queryService,
      StatsService statsService) {
    this.ontologyService = ontologyService;
    this.ingestService = ingestService;
    this.inferenceService = inferenceService;
    this.queryService = queryService;
    this.statsService = statsService;
  }

  @Override
  public String getOntology() throws IOException {
    return ontologyService.getOntology();
  }

  @Override
  public IngestResult ingest(String ttl) throws IOException {
    return ingestService.ingest(ttl);
  }

  @Override
  public List<String> listBranches() {
    return ingestService.listBranches();
  }

  @Override
  public boolean hasBranch(String branch) {
    return ingestService.hasBranch(branch);
  }

  @Override
  public String getBranch(String branch) throws IOException {
    return ingestService.getBranch(branch);
  }

  @Override
  public void approve(String branch) {
    if (ingestService.approve(branch)) inferenceService.recomputeInference();
  }

  @Override
  public void reject(String branch) {
    ingestService.reject(branch);
  }

  @Override
  public String getAssertions() throws IOException {
    return ingestService.getAssertions();
  }

  @Override
  public List<OntologyClass> getClassHierarchy() {
    return ontologyService.getClassHierarchy();
  }

  @Override
  public List<String> getInstances(String type) {
    return queryService.getInstances(type);
  }

  @Override
  public List<ProvenancedStatement> describe(String id) {
    return queryService.describe(id);
  }

  @Override
  public String query(String sparql) {
    return queryService.query(sparql);
  }

  @Override
  public String search(String text) {
    return queryService.search(text);
  }

  @Override
  public CortexStats getStats() {
    return statsService.getStats();
  }

  @Override
  public void recomputeInference() {
    inferenceService.recomputeInference();
  }
}
