package ai.chatur.cortex.core.inference;

import ai.chatur.cortex.core.jena.DatasetPatch;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives new statements from the approved assertions by applying a {@link Reasoner}.
 *
 * <p>The inference results are materialized into a separate dataset that serves as the read model
 * for queries and search, leaving the dataset of approved assertions untouched. A single inference
 * model is kept for the lifetime of the service. {@link #addInference(Model)} extends it
 * incrementally as branches are approved, so a single {@link
 * ai.chatur.cortex.CortexBranches#approve approve} never recomputes the closure from scratch.
 * {@link #recomputeInference()} rebuilds it from a fresh in-memory copy of the assertions instead:
 * it runs once at application startup, and as the fallback inside {@code approve} if the
 * incremental update throws. Either way only the difference to the current inference graph is
 * applied, as an RDF patch, so the full-text index wrapped around the inference dataset only ever
 * indexes statements that are genuinely new.
 */
public class InferenceService {

  private static final Logger log = LoggerFactory.getLogger(InferenceService.class);

  private final Dataset assertions;
  private final Dataset inferences;
  private final Reasoner reasoner;

  private InfModel infModel;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions
   * @param inferences the dataset the inference results are materialized into
   * @param reasoner the reasoner used to derive new statements
   */
  public InferenceService(Dataset assertions, Dataset inferences, Reasoner reasoner) {
    this.assertions = assertions;
    this.inferences = inferences;
    this.reasoner = reasoner;
  }

  /**
   * Recomputes the inference closure from the current assertions and patches the inference dataset
   * with the difference: statements no longer entailed are deleted, novel ones are added, and
   * statements already present are left untouched so they are not reindexed.
   *
   * <p>The assertions are copied into memory, so the inference model never reads the transactional
   * assertions dataset after this method returns.
   */
  public synchronized void recomputeInference() {
    long start = System.currentTimeMillis();
    Model base = ModelFactory.createDefaultModel();
    Txn.executeRead(assertions, () -> base.add(assertions.getDefaultModel()));
    infModel = ModelFactory.createInfModel(reasoner, base);
    patchInferences("Recomputed", start);
  }

  /**
   * Extends the inference closure with newly approved assertions and patches the inference dataset
   * with the difference, without recomputing the closure from scratch.
   *
   * <p>Falls back to {@link #recomputeInference()} if the closure has not been computed yet — the
   * assertions dataset already contains the novel statements.
   *
   * @param novel the newly approved assertions
   */
  public synchronized void addInference(Model novel) {
    if (infModel == null) {
      recomputeInference();
      return;
    }
    if (novel.isEmpty()) return;
    long start = System.currentTimeMillis();
    infModel.add(novel);
    patchInferences("Extended", start);
  }

  private void patchInferences(String action, long start) {
    Model inferred = ModelFactory.createDefaultModel();
    inferred.add(infModel);
    Model stale = ModelFactory.createDefaultModel();
    Model novel = ModelFactory.createDefaultModel();
    Txn.executeRead(
        inferences,
        () -> {
          Model current = inferences.getDefaultModel();
          stale.add(current.difference(inferred));
          novel.add(inferred.difference(current));
        });
    DatasetPatch.apply(
        inferences,
        patch -> patch.deleteAll(Quad.defaultGraphIRI, stale).addAll(Quad.defaultGraphIRI, novel));
    log.info(
        "{} inference in {} ms: {} novel and {} stale statements",
        action,
        System.currentTimeMillis() - start,
        novel.size(),
        stale.size());
  }
}
