package ai.chatur.cortex;

import ai.chatur.cortex.core.archive.ArchiveService;
import ai.chatur.cortex.core.branch.BranchEditService;
import ai.chatur.cortex.core.branch.BranchMergeService;
import ai.chatur.cortex.core.branch.BranchQueryService;
import ai.chatur.cortex.core.branch.BranchRepository;
import ai.chatur.cortex.core.inference.InferenceService;
import ai.chatur.cortex.core.inference.ReasonerFactory;
import ai.chatur.cortex.core.inference.RuleLoader;
import ai.chatur.cortex.core.ingest.IngestService;
import ai.chatur.cortex.core.lint.LintService;
import ai.chatur.cortex.core.lint.ShapesLoader;
import ai.chatur.cortex.core.ontology.OntologyLoader;
import ai.chatur.cortex.core.ontology.OntologyService;
import ai.chatur.cortex.core.provenance.ProvenanceRecorder;
import ai.chatur.cortex.core.query.QueryService;
import ai.chatur.cortex.core.stats.StatsService;
import ai.chatur.cortex.core.store.AssertionStore;
import ai.chatur.cortex.core.store.TextIndexFactory;
import ai.chatur.cortex.core.usage.UsageService;
import java.time.Duration;
import java.util.List;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;

/**
 * Builds a {@link Cortex} knowledge graph from ontology, shapes, and rules, without booting Spring.
 *
 * <p>This is the only supported way to assemble a {@link Cortex} outside of the Spring
 * auto-configuration: it loads the ontology, shapes, and rules; opens the assertions store and the
 * in-memory text index; and wires up the same eleven core services {@code
 * ai.chatur.cortex.spring.CortexAutoConfiguration} does. By default everything is held in memory,
 * which makes it well suited to tests that want a fresh, isolated graph without a Spring context.
 *
 * <pre>{@code
 * Cortex cortex = CortexBuilder.create()
 *     .ontologies(List.of(ontologyTurtle))
 *     .shapes(List.of(shapesTurtle))
 *     .rules(List.of(rulesText))
 *     .build();
 * }</pre>
 */
public final class CortexBuilder {

  /**
   * The default half-life of a view's contribution to search ranking.
   *
   * <p>Mirrors {@code cortex.search.view-half-life}, whose default must match: the two assembly
   * paths are required to produce identical graphs.
   */
  public static final Duration DEFAULT_VIEW_HALF_LIFE = Duration.ofDays(30);

  private List<String> ontologies = List.of();
  private List<String> shapes = List.of();
  private List<String> rules = List.of();
  private String assertionsLocation;
  private Duration viewHalfLife = DEFAULT_VIEW_HALF_LIFE;

  private CortexBuilder() {}

  /**
   * Creates a new builder, defaulting to an empty ontology, no shapes, no rules, and a fully
   * in-memory graph.
   *
   * @return the new builder
   */
  public static CortexBuilder create() {
    return new CortexBuilder();
  }

  /**
   * Sets the ontology documents the knowledge graph is built on.
   *
   * @param turtle the ontology documents, in Turtle syntax, merged in order
   * @return this builder, for chaining
   */
  public CortexBuilder ontologies(List<String> turtle) {
    this.ontologies = turtle;
    return this;
  }

  /**
   * Sets the SHACL shapes ingested assertions are validated against.
   *
   * @param turtle the shapes documents, in Turtle syntax, merged in order
   * @return this builder, for chaining
   */
  public CortexBuilder shapes(List<String> turtle) {
    this.shapes = turtle;
    return this;
  }

  /**
   * Sets the Jena rules used for rule-based inference.
   *
   * @param rules the rules documents, in Jena rules syntax, concatenated in order
   * @return this builder, for chaining
   */
  public CortexBuilder rules(List<String> rules) {
    this.rules = rules;
    return this;
  }

  /**
   * Makes the assertions persist to a TDB2 store on disk instead of in memory.
   *
   * <p>The inference closure and text index are always an in-memory cache rebuilt from the
   * assertions, regardless of this setting — see {@link TextIndexFactory}.
   *
   * @param assertionsLocation the directory of the TDB2 store for assertions
   * @return this builder, for chaining
   */
  public CortexBuilder persistent(String assertionsLocation) {
    this.assertionsLocation = assertionsLocation;
    return this;
  }

  /**
   * Sets how long it takes a view's contribution to search ranking to halve.
   *
   * <p>Search results are weighted by how often each resource is deliberately opened; this controls
   * how quickly that interest fades, so recent attention counts for more than attention that has
   * since moved on. Pass {@link Duration#ZERO} to disable decay and weight by the raw lifetime
   * count instead.
   *
   * @param viewHalfLife the half-life, or {@link Duration#ZERO} to disable decay
   * @return this builder, for chaining
   */
  public CortexBuilder viewHalfLife(Duration viewHalfLife) {
    this.viewHalfLife = viewHalfLife;
    return this;
  }

  /**
   * Assembles the knowledge graph.
   *
   * @return the assembled {@link Cortex}
   */
  public Cortex build() {
    OntModel ontModel = OntologyLoader.load(ontologies);
    Shapes parsedShapes = ShapesLoader.load(shapes);
    List<Rule> parsedRules = RuleLoader.load(rules);
    GenericRuleReasoner genericRuleReasoner = ReasonerFactory.create(parsedRules);
    Reasoner reasoner = genericRuleReasoner.bindSchema(ontModel);

    Dataset assertions =
        AssertionStore.open(assertionsLocation != null, assertionsLocation, ontModel);
    Dataset inferences = TextIndexFactory.open(DatasetFactory.createTxnMem());

    OntologyService ontologyService = new OntologyService(ontModel);
    LintService lintService = new LintService(ontModel, ShaclValidator.get(), parsedShapes);
    ProvenanceRecorder provenanceRecorder = new ProvenanceRecorder();
    IngestService ingestService = new IngestService(assertions, lintService, provenanceRecorder);
    BranchRepository branchRepository = new BranchRepository(assertions);
    BranchQueryService branchQueryService =
        new BranchQueryService(assertions, ontModel, branchRepository);
    BranchEditService branchEditService = new BranchEditService(assertions, branchRepository);
    BranchMergeService branchMergeService =
        new BranchMergeService(assertions, branchRepository, provenanceRecorder);
    ArchiveService archiveService = new ArchiveService(assertions);
    InferenceService inferenceService = new InferenceService(assertions, inferences, reasoner);
    UsageService usageService = new UsageService(assertions, viewHalfLife);
    QueryService queryService = new QueryService(inferences, assertions, ontModel, usageService);
    StatsService statsService =
        new StatsService(assertions, inferences, ontModel, parsedShapes, parsedRules);

    return new JenaCortex(
        ontologyService,
        lintService,
        ingestService,
        branchRepository,
        branchQueryService,
        branchEditService,
        branchMergeService,
        archiveService,
        inferenceService,
        queryService,
        statsService);
  }
}
