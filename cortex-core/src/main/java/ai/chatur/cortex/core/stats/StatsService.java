package ai.chatur.cortex.core.stats;

import ai.chatur.cortex.CortexStats;
import java.util.Calendar;
import java.util.List;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.system.Txn;

/**
 * Computes {@link CortexStats statistics} over the knowledge graph: ingestion activity derived from
 * provenance, the size of the assertion and inference datasets, and the size of the ontology,
 * shapes, and rules the graph is built on.
 */
public class StatsService {

  private static final Query ADDED_TODAY =
      QueryFactory.create(
          """
          PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          PREFIX prov: <http://www.w3.org/ns/prov#>
          SELECT (COUNT(*) AS ?count)
          WHERE {
            ?reifier rdf:reifies ?statement ;
                     prov:wasGeneratedBy ?activity .
            ?activity prov:endedAtTime ?created .
            FILTER (?created >= ?start)
          }
          """);

  private final Dataset assertions;
  private final Dataset inferences;
  private final OntModel ontModel;
  private final Shapes shapes;
  private final List<Rule> rules;

  /**
   * Creates the service.
   *
   * @param assertions the dataset holding the approved assertions and the staged branches
   * @param inferences the dataset holding the assertions enriched by inference
   * @param ontModel the ontology model
   * @param shapes the SHACL shapes ingested assertions are validated against
   * @param rules the rules used for inference
   */
  public StatsService(
      Dataset assertions, Dataset inferences, OntModel ontModel, Shapes shapes, List<Rule> rules) {
    this.assertions = assertions;
    this.inferences = inferences;
    this.ontModel = ontModel;
    this.shapes = shapes;
    this.rules = rules;
  }

  /**
   * Returns a snapshot of the size and activity of the knowledge graph.
   *
   * @return the current statistics
   */
  public CortexStats getStats() {
    return new CortexStats(
        countTriplesAddedToday(),
        countPendingBranches(),
        countAssertionTriples(),
        countInferenceTriples(),
        countOntologyClasses(),
        shapes.numRootShapes(),
        rules.size());
  }

  long countTriplesAddedToday() {
    Literal startOfDay = ResourceFactory.createTypedLiteral(getStartOfDay());
    return Txn.calculateRead(
        assertions,
        () -> {
          QueryExecution queryExecution =
              QueryExecution.dataset(assertions)
                  .query(ADDED_TODAY)
                  .substitution("start", startOfDay)
                  .build();
          try (queryExecution) {
            return queryExecution.execSelect().next().getLiteral("count").getLong();
          }
        });
  }

  Calendar getStartOfDay() {
    Calendar start = Calendar.getInstance();
    start.set(Calendar.HOUR_OF_DAY, 0);
    start.set(Calendar.MINUTE, 0);
    start.set(Calendar.SECOND, 0);
    start.set(Calendar.MILLISECOND, 0);
    return start;
  }

  long countPendingBranches() {
    return Txn.calculateRead(assertions, () -> Iter.count(assertions.listModelNames()));
  }

  long countAssertionTriples() {
    return Txn.calculateRead(assertions, () -> assertions.getDefaultModel().size());
  }

  long countInferenceTriples() {
    return Txn.calculateRead(inferences, () -> inferences.getDefaultModel().size());
  }

  long countOntologyClasses() {
    return ontModel.classes().filter(OntClass::isURIResource).count();
  }
}
