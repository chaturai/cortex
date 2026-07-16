package ai.chatur.cortex.core.branch;

import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.provenance.ProvenanceRecorder;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a branch pending review by merging its staged assertions into the default graph or
 * discarding them.
 */
public class BranchMergeService {

  private static final Logger log = LoggerFactory.getLogger(BranchMergeService.class);

  private final Dataset assertions;
  private final BranchRepository branchRepository;
  private final ProvenanceRecorder provenanceRecorder;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   * @param branchRepository guards every operation against an unknown branch
   * @param provenanceRecorder builds the provenance recorded when a branch is approved
   */
  public BranchMergeService(
      Dataset assertions,
      BranchRepository branchRepository,
      ProvenanceRecorder provenanceRecorder) {
    this.assertions = assertions;
    this.branchRepository = branchRepository;
    this.provenanceRecorder = provenanceRecorder;
  }

  /**
   * Merges the assertions staged on the given branch into the default graph, closing the {@link
   * ai.chatur.cortex.core.PROV#Activity provenance activity} of the ingestion and recording the
   * reification of every merged statement, linked to the activity, in the {@link
   * CortexNamespace#PROVENANCE provenance graph}, and deletes the branch.
   *
   * <p>The novelty diff, the merge, and the branch deletion all happen inside one write transaction
   * on the assertions dataset: TDB2 serializes writers, so two branches can never be approved
   * concurrently, and a failure partway through aborts the whole transaction rather than leaving
   * the data merged while the branch is still pending (which used to risk a second, duplicate
   * {@code prov:Activity} on a retried approval). Staged triples that were approved through another
   * branch in the meantime are skipped, so a statement is never merged or reified twice — this is
   * now guaranteed by that same serialization, rather than a best-effort race with the other
   * branch's approval.
   *
   * @param branch the branch name
   * @return the newly approved assertions — empty if every staged triple was approved through
   *     another branch in the meantime — or {@code null} if the branch does not exist
   */
  public Model approve(String branch) {
    return branchRepository.onBranch(
        branch,
        "approve",
        namedModel ->
            Txn.calculateWrite(
                assertions,
                () -> {
                  Model diff =
                      assertions.getNamedModel(namedModel).difference(assertions.getDefaultModel());
                  Model data = getData(diff, namedModel);
                  Model provenance = provenanceRecorder.getProvenance(diff, data, namedModel);
                  assertions.getDefaultModel().add(data);
                  assertions.getNamedModel(CortexNamespace.PROVENANCE).add(provenance);
                  assertions.removeNamedModel(namedModel);
                  log.info("Approved branch {}", branch);
                  return data;
                }),
        null);
  }

  /**
   * Filters the branch's own provenance activity out of its staged statements, leaving only the
   * actual data to merge.
   *
   * @param diff the branch's staged statements not already present in the default graph
   * @param activity the branch's own provenance activity resource
   * @return {@code diff} without the statements describing {@code activity}
   */
  Model getData(Model diff, Resource activity) {
    Model data = ModelFactory.createDefaultModel();
    diff.listStatements()
        .forEach(
            statement -> {
              if (!activity.equals(statement.getSubject())) data.add(statement);
            });
    return data;
  }

  /**
   * Rejects the given branch, discarding its staged assertions, if it exists.
   *
   * @param branch the branch name
   */
  public void reject(String branch) {
    branchRepository.onBranch(
        branch,
        "reject",
        namedModel -> {
          Txn.executeWrite(assertions, () -> assertions.removeNamedModel(namedModel));
          log.info("Rejected branch {}", branch);
          return null;
        },
        null);
  }
}
