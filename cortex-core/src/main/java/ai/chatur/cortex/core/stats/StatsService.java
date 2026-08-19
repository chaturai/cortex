package ai.chatur.cortex.core.stats;

import ai.chatur.cortex.CortexStats;
import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.jena.Sparql;
import java.util.Calendar;
import java.util.List;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
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
            GRAPH <cortex://provenance> {
              ?reifier rdf:reifies ?statement ;
                       prov:wasGeneratedBy ?activity .
              ?activity prov:endedAtTime ?created .
              FILTER (?created >= ?start)
            }
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
        countInferredTriples(),
        countOntologyClasses(),
        shapes.numRootShapes(),
        rules.size());
  }

  long countTriplesAddedToday() {
    Literal startOfDay = ResourceFactory.createTypedLiteral(getStartOfDay());
    return Sparql.on(assertions, ADDED_TODAY)
        .bind("start", startOfDay)
        .execute(
            queryExecution -> queryExecution.execSelect().next().getLiteral("count").getLong());
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
    // Count only real branches, exactly as BranchRepository.list does. The assertions dataset also
    // holds reserved named graphs that are not branches: cortex://provenance, cortex://usage, and
    // each branch's cortex://retract-<uuid>. Excluding only provenance let the usage graph —
    // created
    // the first time any resource is viewed — show as a phantom pending branch on the home page.
    return Txn.calculateRead(
        assertions,
        () -> Iter.count(Iter.filter(assertions.listModelNames(), CortexNamespace::isBranch)));
  }

  long countAssertionTriples() {
    return Txn.calculateRead(assertions, () -> assertions.getDefaultModel().size());
  }

  /**
   * Counts what the reasoner actually derived: the inference dataset less the approved assertions
   * it was computed from, and less the ontology's own axioms.
   *
   * <p>The ontology is bound to the reasoner as its schema, so its axioms are materialized into the
   * inference dataset alongside the conclusions drawn from them — counting the dataset whole
   * reports every class declaration and every {@code rdfs:domain} as something inference produced.
   *
   * <p>Clamped at zero: an {@code approve} merges into the assertions and extends the closure in
   * two steps, so a snapshot taken between them can momentarily see more assertions than the
   * closure has caught up with.
   */
  long countInferredTriples() {
    long total = Txn.calculateRead(inferences, () -> inferences.getDefaultModel().size());
    return Math.max(0, total - countAssertionTriples() - countOntologyTriples());
  }

  /**
   * Counts the ontology axioms materialized into the inference dataset, skipping any that were also
   * approved as assertions — {@link #countAssertionTriples()} already counts those, and the
   * inference dataset holds one copy.
   */
  long countOntologyTriples() {
    List<Statement> axioms =
        Txn.calculateRead(
            inferences,
            () -> {
              Model model = inferences.getDefaultModel();
              return ontModel.listStatements().filterKeep(model::contains).toList();
            });
    return Txn.calculateRead(
        assertions,
        () -> {
          Model model = assertions.getDefaultModel();
          return axioms.stream().filter(axiom -> !model.contains(axiom)).count();
        });
  }

  long countOntologyClasses() {
    return ontModel.classes().filter(OntClass::isURIResource).count();
  }
}
