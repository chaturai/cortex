package ai.chatur.cortex.core.inference;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives new statements from the approved assertions by applying a {@link Reasoner}.
 *
 * <p>The inference results are materialized into a separate dataset that serves as the read model
 * for queries and search, leaving the dataset of approved assertions untouched.
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

  /** Rebuilds the inference dataset from the current assertions. */
  public void recomputeInference() {
    long start = System.currentTimeMillis();
    Txn.executeWrite(
        inferences,
        () -> {
          Txn.executeRead(
              assertions,
              () -> {
                Model model = assertions.getDefaultModel();
                InfModel inf = ModelFactory.createInfModel(reasoner, model);
                inferences.setDefaultModel(inf);
              });
        });
    log.info("Recomputed inference in {} ms", System.currentTimeMillis() - start);
  }
}
