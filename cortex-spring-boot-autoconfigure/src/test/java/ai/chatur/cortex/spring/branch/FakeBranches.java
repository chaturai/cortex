package ai.chatur.cortex.spring.branch;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchInfo;
import ai.chatur.cortex.BranchRename;
import ai.chatur.cortex.BranchSubject;
import ai.chatur.cortex.CortexBranches;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-rolled fake of {@link CortexBranches}, shared by {@link BranchControllerTests} and {@link
 * BranchEditControllerTests}. Backed by simple maps rather than a real graph, and records the
 * arguments of the last mutating call so tests can assert the controller delegated correctly.
 */
final class FakeBranches implements CortexBranches {

  private final List<String> branches;
  private final Map<String, BranchInfo> infoByBranch;
  private final Map<String, List<BranchSubject>> subjectsByBranch;
  private final Set<String> existingBranches;

  String approvedBranch;
  String rejectedBranch;
  String lastUpdatedBranch;
  List<BranchChange> lastUpdateChanges;
  String lastRenamedBranch;
  List<BranchRename> lastRenames;

  FakeBranches(
      List<String> branches,
      Map<String, BranchInfo> infoByBranch,
      Map<String, List<BranchSubject>> subjectsByBranch,
      Set<String> existingBranches) {
    this.branches = branches;
    this.infoByBranch = infoByBranch;
    this.subjectsByBranch = subjectsByBranch;
    this.existingBranches = existingBranches;
  }

  static FakeBranches withNoBranches() {
    return new FakeBranches(List.of(), Map.of(), Map.of(), Set.of());
  }

  @Override
  public List<String> listBranches() {
    return branches;
  }

  @Override
  public boolean hasBranch(String branch) {
    return existingBranches.contains(branch);
  }

  @Override
  public String getBranch(String branch) {
    throw new UnsupportedOperationException("not exercised by any controller under test");
  }

  @Override
  public BranchInfo getBranchInfo(String branch) {
    return infoByBranch.get(branch);
  }

  @Override
  public List<BranchSubject> getBranchSubjects(String branch) {
    return subjectsByBranch.getOrDefault(branch, List.of());
  }

  @Override
  public boolean updateBranch(String branch, List<BranchChange> changes) {
    lastUpdatedBranch = branch;
    lastUpdateChanges = changes;
    return existingBranches.contains(branch);
  }

  @Override
  public boolean renameBranchSubjects(String branch, List<BranchRename> renames) {
    lastRenamedBranch = branch;
    lastRenames = renames;
    return existingBranches.contains(branch);
  }

  @Override
  public void approve(String branch) {
    approvedBranch = branch;
  }

  @Override
  public void reject(String branch) {
    rejectedBranch = branch;
  }
}
