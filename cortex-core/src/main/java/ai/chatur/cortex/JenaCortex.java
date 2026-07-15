package ai.chatur.cortex;

import ai.chatur.cortex.core.inference.InferenceService;
import ai.chatur.cortex.core.ingest.IngestService;
import ai.chatur.cortex.core.lint.LintService;
import ai.chatur.cortex.core.ontology.OntologyService;
import ai.chatur.cortex.core.query.QueryService;
import ai.chatur.cortex.core.stats.StatsService;
import java.io.IOException;
import java.util.List;
import org.apache.jena.rdf.model.Model;

/**
 * {@link Cortex} implementation backed by <a href="https://jena.apache.org">Apache Jena</a>.
 *
 * <p>Composes the core services: {@link OntologyService} for the ontology and its class hierarchy,
 * {@link LintService} for linting assertions against the ontology, {@link IngestService} for
 * validated, branch-based ingestion with provenance, {@link InferenceService} for rule-based
 * inference, {@link QueryService} for SPARQL queries and full-text search, and {@link StatsService}
 * for statistics. Approving a branch extends the inference closure incrementally with the newly
 * approved statements.
 */
public class JenaCortex implements Cortex {

  private final OntologyService ontologyService;
  private final LintService lintService;
  private final IngestService ingestService;
  private final InferenceService inferenceService;
  private final QueryService queryService;
  private final StatsService statsService;

  public JenaCortex(
      OntologyService ontologyService,
      LintService lintService,
      IngestService ingestService,
      InferenceService inferenceService,
      QueryService queryService,
      StatsService statsService) {
    this.ontologyService = ontologyService;
    this.lintService = lintService;
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
  public LintResult lint(String ttl) throws IOException {
    return lintService.lint(ttl);
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
  public BranchInfo getBranchInfo(String branch) {
    return ingestService.getBranchInfo(branch);
  }

  @Override
  public List<BranchSubject> getBranchSubjects(String branch) {
    return ingestService.getBranchSubjects(branch);
  }

  @Override
  public boolean updateBranch(String branch, List<BranchChange> changes) {
    return ingestService.updateBranch(branch, changes);
  }

  @Override
  public boolean renameBranchSubjects(String branch, List<BranchRename> renames) {
    return ingestService.renameBranchSubjects(branch, renames);
  }

  @Override
  public void approve(String branch) {
    Model novel = ingestService.approve(branch);
    if (novel != null) inferenceService.addInference(novel);
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
  public String exportAssertions() throws IOException {
    return ingestService.exportAssertions();
  }

  @Override
  public void importAssertions(String trig) {
    ingestService.importAssertions(trig);
    inferenceService.recomputeInference();
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
  public List<SearchResult> searchSubjects(String text) {
    return queryService.searchSubjects(text);
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
