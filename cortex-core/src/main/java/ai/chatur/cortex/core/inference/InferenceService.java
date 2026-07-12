package ai.chatur.cortex.core.inference;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.system.Txn;

public class InferenceService {

  private final Dataset assertions;
  private final Dataset inferences;
  private final Reasoner reasoner;

  public InferenceService(Dataset assertions, Dataset inferences, Reasoner reasoner) {
    this.assertions = assertions;
    this.inferences = inferences;
    this.reasoner = reasoner;
  }

  public void recomputeInference() {
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
  }
}
