package ai.chatur.cortex;

import ai.chatur.cortex.core.archive.ArchiveService;
import ai.chatur.cortex.core.branch.BranchEditService;
import ai.chatur.cortex.core.branch.BranchMergeService;
import ai.chatur.cortex.core.branch.BranchQueryService;
import ai.chatur.cortex.core.branch.BranchRepository;
import ai.chatur.cortex.core.inference.InferenceService;
import ai.chatur.cortex.core.ingest.IngestService;
import ai.chatur.cortex.core.lint.LintService;
import ai.chatur.cortex.core.ontology.OntologyService;
import ai.chatur.cortex.core.query.QueryService;
import ai.chatur.cortex.core.stats.StatsService;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Cortex} implementation backed by <a href="https://jena.apache.org">Apache Jena</a>.
 *
 * <p>Composes the core services: {@link OntologyService} for the ontology and its class hierarchy,
 * {@link LintService} for linting assertions against the ontology, {@link IngestService} for
 * validated ingestion, {@link BranchRepository} for branch existence and listing, {@link
 * BranchQueryService} for reading a branch pending review, {@link BranchEditService} for reviewer
 * edits to a branch, {@link BranchMergeService} for approving or rejecting a branch, {@link
 * ArchiveService} for backup and restore, {@link InferenceService} for rule-based inference, {@link
 * QueryService} for SPARQL queries and full-text search, and {@link StatsService} for statistics.
 * Approving a branch extends the inference closure incrementally with the newly approved
 * statements.
 */
public class JenaCortex implements Cortex {

  private static final Logger log = LoggerFactory.getLogger(JenaCortex.class);

  private final OntologyService ontologyService;
  private final LintService lintService;
  private final IngestService ingestService;
  private final BranchRepository branchRepository;
  private final BranchQueryService branchQueryService;
  private final BranchEditService branchEditService;
  private final BranchMergeService branchMergeService;
  private final ArchiveService archiveService;
  private final InferenceService inferenceService;
  private final QueryService queryService;
  private final StatsService statsService;

  /**
   * Creates the composed implementation.
   *
   * @param ontologyService the ontology and its class hierarchy
   * @param lintService linting of assertions against the ontology
   * @param ingestService validated, branch-based ingestion
   * @param branchRepository branch existence and listing
   * @param branchQueryService reading the assertions staged on a branch
   * @param branchEditService reviewer edits to a branch
   * @param branchMergeService approving or rejecting a branch
   * @param archiveService backup and restore of the assertions dataset
   * @param inferenceService rule-based inference over the approved assertions
   * @param queryService SPARQL queries and full-text search
   * @param statsService knowledge graph statistics
   */
  public JenaCortex(
      OntologyService ontologyService,
      LintService lintService,
      IngestService ingestService,
      BranchRepository branchRepository,
      BranchQueryService branchQueryService,
      BranchEditService branchEditService,
      BranchMergeService branchMergeService,
      ArchiveService archiveService,
      InferenceService inferenceService,
      QueryService queryService,
      StatsService statsService) {
    this.ontologyService = ontologyService;
    this.lintService = lintService;
    this.ingestService = ingestService;
    this.branchRepository = branchRepository;
    this.branchQueryService = branchQueryService;
    this.branchEditService = branchEditService;
    this.branchMergeService = branchMergeService;
    this.archiveService = archiveService;
    this.inferenceService = inferenceService;
    this.queryService = queryService;
    this.statsService = statsService;
  }

  @Override
  public String getOntology() {
    return ontologyService.getOntology();
  }

  @Override
  public LintResult lint(String ttl) {
    return lintService.lint(ttl);
  }

  @Override
  public IngestResult ingest(String ttl) {
    return ingestService.ingest(ttl);
  }

  @Override
  public List<String> listBranches() {
    return branchRepository.list();
  }

  @Override
  public boolean hasBranch(String branch) {
    return branchRepository.exists(branch);
  }

  @Override
  public String getBranch(String branch) {
    return branchQueryService.getBranch(branch);
  }

  @Override
  public BranchInfo getBranchInfo(String branch) {
    return branchQueryService.getBranchInfo(branch);
  }

  @Override
  public List<BranchSubject> getBranchSubjects(String branch) {
    return branchQueryService.getBranchSubjects(branch);
  }

  @Override
  public boolean updateBranch(String branch, List<BranchChange> changes) {
    return branchEditService.updateBranch(branch, changes);
  }

  @Override
  public boolean renameBranchSubjects(String branch, List<BranchRename> renames) {
    return branchEditService.renameBranchSubjects(branch, renames);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The merge itself ({@link BranchMergeService#approve}) is one atomic write transaction: it
   * either fully commits or leaves the branch untouched. Extending the inference closure afterward,
   * however, is a second, separate transaction against a different dataset — the two cannot be
   * combined into one transaction, since {@link InferenceService} and {@link BranchMergeService}
   * guard independent {@code Dataset} instances. If {@link InferenceService#addInference} throws,
   * the branch is already merged and cannot be un-merged here; rather than leave the closure
   * silently stale until restart, this recomputes it from scratch — which reads the assertions
   * dataset the merge already committed to, so the closure is correct again even though the
   * incremental update failed — and then rethrows so the caller still learns the approval did not
   * complete cleanly.
   */
  @Override
  public void approve(String branch) {
    Model novel = branchMergeService.approve(branch);
    if (novel == null) return;
    try {
      inferenceService.addInference(novel);
    } catch (RuntimeException e) {
      log.error(
          "Extending the inference closure after approving branch {} failed; recomputing it from"
              + " scratch so it does not stay silently stale until restart",
          branch,
          e);
      try {
        inferenceService.recomputeInference();
      } catch (RuntimeException recomputeFailure) {
        recomputeFailure.addSuppressed(e);
        throw recomputeFailure;
      }
      throw e;
    }
  }

  @Override
  public void reject(String branch) {
    branchMergeService.reject(branch);
  }

  @Override
  public String getAssertions() {
    return archiveService.getAssertions();
  }

  @Override
  public String exportAssertions() {
    return archiveService.exportAssertions();
  }

  @Override
  public void importAssertions(String trig) {
    archiveService.importAssertions(trig);
    inferenceService.recomputeInference();
  }

  @Override
  public List<OntologyClass> getClassHierarchy() {
    return ontologyService.getClassHierarchy();
  }

  @Override
  public List<Term> getInstances(String type) {
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
