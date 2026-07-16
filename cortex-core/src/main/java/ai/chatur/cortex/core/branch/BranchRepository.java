package ai.chatur.cortex.core.branch;

import ai.chatur.cortex.core.CortexNamespace;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks up the branches pending review — named graphs within the assertions dataset — guarding
 * every access against the {@link CortexNamespace#PROVENANCE provenance graph}, which is never a
 * branch even though it is a named graph of the same dataset.
 */
public class BranchRepository {

  private static final Logger log = LoggerFactory.getLogger(BranchRepository.class);

  private final Dataset assertions;

  /**
   * Creates the repository.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   */
  public BranchRepository(Dataset assertions) {
    this.assertions = assertions;
  }

  /**
   * Applies the action to the branch graph if a branch with that name is pending, and returns the
   * missing value otherwise.
   *
   * <p>The {@link CortexNamespace#PROVENANCE provenance graph} is never a branch: a branch named
   * {@code provenance} is always reported missing, whether or not the provenance graph exists.
   *
   * @param branch the branch name
   * @param operation a short description of the caller's operation, used only in the warning logged
   *     when the branch does not exist
   * @param action applied to the branch's named graph resource if the branch exists
   * @param missing the value to return if the branch does not exist
   * @param <T> the type of the result
   * @return the result of {@code action}, or {@code missing} if the branch does not exist
   */
  public <T> T onBranch(String branch, String operation, Function<Resource, T> action, T missing) {
    Resource namedModel = CortexNamespace.getResource(branch);
    if (!exists(branch)) {
      log.warn("Cannot {} unknown branch {}", operation, branch);
      return missing;
    }
    return action.apply(namedModel);
  }

  /**
   * Returns the names of all branches pending review.
   *
   * <p>The {@link CortexNamespace#PROVENANCE provenance graph} is not a branch and is never listed.
   *
   * @return the branch names, empty if nothing is pending
   */
  public List<String> list() {
    List<String> branches = new ArrayList<>();
    Txn.executeRead(
        assertions,
        () ->
            assertions
                .listModelNames()
                .forEachRemaining(
                    node -> {
                      if (CortexNamespace.PROVENANCE.equals(node)) return;
                      branches.add(node.getLocalName());
                    }));
    return branches;
  }

  /**
   * Reports whether a branch with the given name is pending review.
   *
   * <p>The {@link CortexNamespace#PROVENANCE provenance graph} is not a branch: it is never
   * reported here, which also shields it from every caller that checks this method before mutating
   * or reading a branch — every write in {@code core.branch} and every reader in {@link
   * BranchQueryService} routes through {@link #onBranch}, which calls this method first.
   *
   * @param branch the branch name
   * @return {@code true} if the branch exists
   */
  public boolean exists(String branch) {
    Resource namedModel = CortexNamespace.getResource(branch);
    if (CortexNamespace.PROVENANCE.equals(namedModel)) return false;
    return Txn.calculateRead(assertions, () -> assertions.containsNamedModel(namedModel));
  }
}
