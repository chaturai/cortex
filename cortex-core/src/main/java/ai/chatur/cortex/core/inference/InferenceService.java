package ai.chatur.cortex.core.inference;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives new statements from the approved assertions by applying a {@link Reasoner}.
 *
 * <p>The inference results are materialized into a separate dataset that serves as the read model
 * for queries and search, leaving the dataset of approved assertions untouched. The closure is
 * always computed from the assertions alone and only the difference to the current inference graph
 * is applied, as an RDF patch, so recomputation is idempotent and the full-text index wrapped
 * around the inference dataset only ever indexes statements that are genuinely new.
 */
public class InferenceService {

  private static final Logger log = LoggerFactory.getLogger(InferenceService.class);

  private final Dataset assertions;
  private final Dataset inferences;
  private final Reasoner reasoner;

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
   */
  public void recomputeInference() {
    long start = System.currentTimeMillis();
    Model inferred = ModelFactory.createDefaultModel();
    Txn.executeRead(
        assertions,
        () -> inferred.add(ModelFactory.createInfModel(reasoner, assertions.getDefaultModel())));
    Model stale = ModelFactory.createDefaultModel();
    Model novel = ModelFactory.createDefaultModel();
    Txn.executeRead(
        inferences,
        () -> {
          Model current = inferences.getDefaultModel();
          stale.add(current.difference(inferred));
          novel.add(inferred.difference(current));
        });
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.txnBegin();
    stale.getGraph().stream()
        .forEach(
            triple ->
                collector.delete(
                    Quad.defaultGraphIRI,
                    triple.getSubject(),
                    triple.getPredicate(),
                    triple.getObject()));
    novel.getGraph().stream()
        .forEach(
            triple ->
                collector.add(
                    Quad.defaultGraphIRI,
                    triple.getSubject(),
                    triple.getPredicate(),
                    triple.getObject()));
    collector.txnCommit();
    RDFPatchOps.applyChange(inferences.asDatasetGraph(), collector.getRDFPatch());
    log.info(
        "Recomputed inference in {} ms: {} novel and {} stale statements",
        System.currentTimeMillis() - start,
        novel.size(),
        stale.size());
  }
}
