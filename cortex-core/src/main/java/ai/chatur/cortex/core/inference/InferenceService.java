package ai.chatur.cortex.core.inference;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives new statements from the approved assertions by reasoning in two stages: first an OWL-Full
 * reasoner bound to the ontology computes the OWL closure of the assertions, then the configured
 * rule reasoner is applied to that closure, without rebinding the ontology as schema.
 *
 * <p>The inference results are materialized into a separate dataset that serves as the read model
 * for queries and search, leaving the dataset of approved assertions untouched. The closure is
 * always recomputed from the assertions and the ontology alone and only the difference to the
 * current inference graph is applied, as an RDF patch, so recomputation is idempotent and the
 * full-text index wrapped around the inference dataset only ever indexes statements that are
 * genuinely new.
 */
public class InferenceService {

  private static final Logger log = LoggerFactory.getLogger(InferenceService.class);

  private final Dataset assertions;
  private final Dataset inferences;
  private final Reasoner owlReasoner;
  private final Reasoner ruleReasoner;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions
   * @param inferences the dataset the inference results are materialized into
   * @param ruleReasoner the reasoner applying the configured inference rules
   * @param ontModel the ontology the OWL-Full reasoner is bound to
   */
  public InferenceService(
      Dataset assertions, Dataset inferences, Reasoner ruleReasoner, Model ontModel) {
    this.assertions = assertions;
    this.inferences = inferences;
    this.owlReasoner = ReasonerRegistry.getOWLReasoner().bindSchema(ontModel);
    this.ruleReasoner = ruleReasoner;
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
        () -> {
          Model owlClosure = ModelFactory.createInfModel(owlReasoner, assertions.getDefaultModel());
          inferred.add(ModelFactory.createInfModel(ruleReasoner, owlClosure));
        });
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
