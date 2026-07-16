package ai.chatur.cortex;

import java.util.List;

/** Lists, inspects, edits, and resolves the review branches staged by ingestion. */
public interface CortexBranches {

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
   */
  String getBranch(String branch);

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
   * Rejects the given branch, discarding its staged assertions.
   *
   * <p>Does nothing if the branch does not exist.
   *
   * @param branch the branch name
   */
  void reject(String branch);
}
