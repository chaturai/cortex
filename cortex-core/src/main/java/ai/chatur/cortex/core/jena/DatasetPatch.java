package ai.chatur.cortex.core.jena;

import java.util.function.Consumer;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.system.Txn;

/**
 * Collects additions and deletions as an RDF patch and applies them to a {@link Dataset} in one
 * write transaction.
 *
 * <p>Recording the changes and applying them are two separate steps: {@link #add} and {@link
 * #delete} only record the change, and the patch is played onto the dataset — as a single write
 * transaction on {@code dataset} — only once the recording {@link Consumer} returns. This lets
 * callers build up a patch from several sources — several models, a filtered stream of a live graph
 * — without holding the dataset's write lock while they do it.
 */
public final class DatasetPatch {

  private final RDFChangesCollector collector = new RDFChangesCollector();

  private DatasetPatch() {}

  /**
   * Records changes and applies them to the dataset as a single patch.
   *
   * @param dataset the dataset to patch
   * @param changes records the additions and deletions to apply
   */
  public static void apply(Dataset dataset, Consumer<DatasetPatch> changes) {
    DatasetPatch patch = new DatasetPatch();
    patch.collector.txnBegin();
    changes.accept(patch);
    patch.collector.txnCommit();
    RDFPatchOps.applyChange(dataset.asDatasetGraph(), patch.collector.getRDFPatch());
  }

  /**
   * Records changes inside a read transaction on the dataset, then applies them as one patch.
   *
   * <p>Use this instead of {@link #apply} when recording the changes needs to read the dataset
   * being patched — for example, streaming the triples of a live named graph to decide what to
   * delete. The read transaction covers only the recording; applying the resulting patch is a
   * separate write transaction, opened after the read transaction has closed.
   *
   * @param dataset the dataset to patch
   * @param changes records the additions and deletions to apply, with a read transaction on {@code
   *     dataset} held open for the duration
   */
  public static void applyReading(Dataset dataset, Consumer<DatasetPatch> changes) {
    DatasetPatch patch = new DatasetPatch();
    patch.collector.txnBegin();
    Txn.executeRead(dataset, () -> changes.accept(patch));
    patch.collector.txnCommit();
    RDFPatchOps.applyChange(dataset.asDatasetGraph(), patch.collector.getRDFPatch());
  }

  /**
   * Records the addition of a triple to the given graph.
   *
   * @param graph the named graph the triple belongs to, or {@code Quad.defaultGraphIRI} for the
   *     default graph
   * @param subject the subject of the triple
   * @param predicate the predicate of the triple
   * @param object the object of the triple
   * @return this patch, for chaining
   */
  public DatasetPatch add(Node graph, Node subject, Node predicate, Node object) {
    collector.add(graph, subject, predicate, object);
    return this;
  }

  /**
   * Records the deletion of a triple from the given graph.
   *
   * @param graph the named graph the triple belongs to, or {@code Quad.defaultGraphIRI} for the
   *     default graph
   * @param subject the subject of the triple
   * @param predicate the predicate of the triple
   * @param object the object of the triple
   * @return this patch, for chaining
   */
  public DatasetPatch delete(Node graph, Node subject, Node predicate, Node object) {
    collector.delete(graph, subject, predicate, object);
    return this;
  }

  /**
   * Records the addition of every triple of the model to the given graph.
   *
   * @param graph the named graph to add the triples to, or {@code Quad.defaultGraphIRI} for the
   *     default graph
   * @param model the model whose triples are added
   * @return this patch, for chaining
   */
  public DatasetPatch addAll(Node graph, Model model) {
    model.getGraph().stream()
        .forEach(
            triple -> add(graph, triple.getSubject(), triple.getPredicate(), triple.getObject()));
    return this;
  }

  /**
   * Records the deletion of every triple of the model from the given graph.
   *
   * @param graph the named graph to delete the triples from, or {@code Quad.defaultGraphIRI} for
   *     the default graph
   * @param model the model whose triples are deleted
   * @return this patch, for chaining
   */
  public DatasetPatch deleteAll(Node graph, Model model) {
    model.getGraph().stream()
        .forEach(
            triple ->
                delete(graph, triple.getSubject(), triple.getPredicate(), triple.getObject()));
    return this;
  }
}
