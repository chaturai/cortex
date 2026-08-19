package ai.chatur.cortex.core.branch;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchRename;
import ai.chatur.cortex.core.CortexNamespace;
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
   * <p>A change flagged {@link BranchChange#retracted() retracted} addresses the statements the
   * branch stages for <em>removal</em> from the approved assertions rather than those it stages for
   * addition, and can only cancel one: a retraction has no object of its own to edit, because it
   * names a statement that is already approved.
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
          Node retractions = CortexNamespace.getRetractions(namedModel).asNode();
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
                  Node graph = change.retracted() ? retractions : namedModel.asNode();
                  patch.delete(graph, subject, predicate, toNode(change.object(), change));
                  if (change.retracted()) {
                    if (change.newObject() != null) {
                      log.warn("Ignoring edit to a statement branch {} stages for removal", branch);
                    }
                    continue;
                  }
                  if (change.newObject() != null) {
                    patch.add(graph, subject, predicate, toNode(change.newObject(), change));
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
   * <p>Every staged statement in which a renamed IRI appears is rewritten to use the new IRI, in
   * both positions: the statements describing the renamed subject keep their content under the new
   * IRI, and the statements referencing it as object come to reference the new IRI.
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
                            patch.add(
                                namedModel.asNode(),
                                renamed.getOrDefault(triple.getSubject(), triple.getSubject()),
                                triple.getPredicate(),
                                renamed.getOrDefault(triple.getObject(), triple.getObject()));
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
   * <p>A language tag wins over the datatype, because the datatype a language-tagged literal
   * reports is {@code rdf:langString}, which cannot reconstruct the node on its own: building the
   * literal from it produces a node that does not equal the one staged, so the deletion silently
   * fails to match and the edit leaves the original statement in place.
   *
   * @param value the IRI, or the literal's lexical form
   * @param change the change describing whether {@code value} is a literal, and its language tag or
   *     datatype
   * @return the node {@code value} denotes
   */
  Node toNode(String value, BranchChange change) {
    if (!change.literal()) return NodeFactory.createURI(value);
    if (change.language() != null) return NodeFactory.createLiteralLang(value, change.language());
    if (change.datatype() == null) return NodeFactory.createLiteralString(value);
    return NodeFactory.createLiteralDT(value, NodeFactory.getType(change.datatype()));
  }
}
