package ai.chatur.cortex.core.branch;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchRename;
import ai.chatur.cortex.core.jena.DatasetPatch;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies reviewer edits — deletions, object edits, and subject renames — to the assertions staged
 * on a branch pending review.
 */
public class BranchEditService {

  private static final Logger log = LoggerFactory.getLogger(BranchEditService.class);

  private final Dataset assertions;
  private final BranchRepository branchRepository;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   * @param branchRepository guards every operation against an unknown branch
   */
  public BranchEditService(Dataset assertions, BranchRepository branchRepository) {
    this.assertions = assertions;
    this.branchRepository = branchRepository;
  }

  /**
   * Applies reviewer changes — deletions and object edits — to the assertions staged on the given
   * branch, as an RDF patch on the branch graph.
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
  public boolean updateBranch(String branch, List<BranchChange> changes) {
    return branchRepository.onBranch(
        branch,
        "update",
        namedModel -> {
          Set<Node> deletionSubjects = new HashSet<>();
          DatasetPatch.apply(
              assertions,
              patch -> {
                for (BranchChange change : changes) {
                  if (namedModel.getURI().equals(change.subject())) {
                    log.warn("Ignoring change to the provenance activity of branch {}", branch);
                    continue;
                  }
                  Node subject = NodeFactory.createURI(change.subject());
                  Node predicate = NodeFactory.createURI(change.predicate());
                  patch.delete(
                      namedModel.asNode(), subject, predicate, toNode(change.object(), change));
                  if (change.newObject() != null) {
                    patch.add(
                        namedModel.asNode(),
                        subject,
                        predicate,
                        toNode(change.newObject(), change));
                  } else {
                    deletionSubjects.add(subject);
                  }
                }
              });
          removeDanglingReferences(namedModel, deletionSubjects);
          log.info("Updated branch {} with {} changes", branch, changes.size());
          return true;
        },
        false);
  }

  /**
   * Deletes every statement staged on the branch whose object is an IRI that no longer appears as
   * the subject of any staged statement — the references left dangling when a reviewer's deletions
   * removed a subject entirely.
   *
   * @param namedModel the branch graph
   * @param deletionSubjects the subjects addressed by deletions, candidates for having been removed
   */
  void removeDanglingReferences(Resource namedModel, Set<Node> deletionSubjects) {
    if (deletionSubjects.isEmpty()) return;
    DatasetPatch.applyReading(
        assertions,
        patch -> {
          Graph graph = assertions.getNamedModel(namedModel).getGraph();
          deletionSubjects.stream()
              .filter(node -> !graph.contains(node, Node.ANY, Node.ANY))
              .forEach(
                  node ->
                      graph.stream(Node.ANY, Node.ANY, node)
                          .forEach(
                              triple ->
                                  patch.delete(
                                      namedModel.asNode(),
                                      triple.getSubject(),
                                      triple.getPredicate(),
                                      triple.getObject())));
        });
  }

  /**
   * Renames subjects staged on the given branch, as an RDF patch on the branch graph.
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
  public boolean renameBranchSubjects(String branch, List<BranchRename> renames) {
    return branchRepository.onBranch(
        branch,
        "rename subjects on",
        namedModel -> {
          Map<Node, Node> renamed = new HashMap<>();
          for (BranchRename rename : renames) {
            if (namedModel.getURI().equals(rename.subject())) {
              log.warn("Ignoring rename of the provenance activity of branch {}", branch);
              continue;
            }
            renamed.put(
                NodeFactory.createURI(rename.subject()),
                NodeFactory.createURI(rename.newSubject()));
          }
          DatasetPatch.applyReading(
              assertions,
              patch ->
                  assertions.getNamedModel(namedModel).getGraph().stream()
                      .filter(
                          triple ->
                              renamed.containsKey(triple.getSubject())
                                  || renamed.containsKey(triple.getObject()))
                      .forEach(
                          triple -> {
                            patch.delete(
                                namedModel.asNode(),
                                triple.getSubject(),
                                triple.getPredicate(),
                                triple.getObject());
                            if (!renamed.containsKey(triple.getSubject())) {
                              patch.add(
                                  namedModel.asNode(),
                                  triple.getSubject(),
                                  triple.getPredicate(),
                                  renamed.get(triple.getObject()));
                            }
                          }));
          log.info("Renamed {} subjects on branch {}", renamed.size(), branch);
          return true;
        },
        false);
  }

  /**
   * Builds the RDF node a reviewer-supplied value denotes: an IRI, or the appropriately typed
   * literal.
   *
   * @param value the IRI, or the literal's lexical form
   * @param change the change describing whether {@code value} is a literal and its datatype
   * @return the node {@code value} denotes
   */
  Node toNode(String value, BranchChange change) {
    if (!change.literal()) return NodeFactory.createURI(value);
    if (change.datatype() == null) return NodeFactory.createLiteralString(value);
    return NodeFactory.createLiteralDT(value, NodeFactory.getType(change.datatype()));
  }
}
