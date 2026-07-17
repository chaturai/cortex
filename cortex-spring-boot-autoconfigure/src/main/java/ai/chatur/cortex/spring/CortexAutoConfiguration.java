package ai.chatur.cortex.spring;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.CortexInference;
import ai.chatur.cortex.JenaCortex;
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
import ai.chatur.cortex.spring.inference.InferenceInitializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

/**
 * Core auto-configuration for Cortex: the ontology, SHACL shapes, rule reasoner, the two Jena
 * {@link Dataset}s, the eleven core services, and the composed {@link Cortex} bean.
 *
 * <p>Every bean here is {@link ConditionalOnMissingBean @ConditionalOnMissingBean}, so a consumer
 * may substitute their own {@link OntModel}, {@link Shapes}, {@link Dataset}, {@link
 * GenericRuleReasoner}, or {@link Cortex} simply by declaring a bean of that type — auto-configured
 * beans that depend on it then wire against the supplied instance instead of building their own.
 *
 * <p><strong>The two {@code Dataset} beans are matched by bean name, not by type</strong> (see
 * {@link #assertions(CortexProperties, OntModel)} and {@link #inferences()}): a bare
 * {@code @ConditionalOnMissingBean} matches on return type and is evaluated in bean-definition
 * order, so {@code inferences} would silently back off because {@code assertions} already
 * registered a {@code Dataset} — the graph would boot with {@code inferences == assertions}, and
 * every search would return nothing without ever throwing. Naming the conditions keeps the two
 * independent.
 *
 * <p>Neither {@code Dataset} bean is {@code @Primary}, so a downstream consumer injecting a bare
 * {@code Dataset} (rather than {@code @Qualifier("assertions")} or
 * {@code @Qualifier("inferences")}) gets a {@code NoUniqueBeanDefinitionException}. That is
 * intentional: it forces the caller to say which dataset they mean rather than silently getting one
 * of the two.
 *
 * <p>{@link ai.chatur.cortex.spring.web.CortexWebAutoConfiguration} and {@link
 * ai.chatur.cortex.spring.mcp.CortexMcpAutoConfiguration} both run {@link AutoConfiguration#after()
 * after} this configuration, so the core beans declared here are always available to the
 * controllers and MCP tools they register.
 */
@AutoConfiguration
@EnableConfigurationProperties(CortexProperties.class)
public class CortexAutoConfiguration {

  /** Creates the auto-configuration. Spring instantiates this; consumers do not. */
  public CortexAutoConfiguration() {}

  /**
   * Creates the ontology model by reading the configured resources and delegating to {@link
   * OntologyLoader}.
   *
   * @param properties the Cortex configuration properties
   * @return the merged, locked ontology model
   * @throws IOException if a configured ontology resource cannot be read
   */
  @Bean
  @ConditionalOnMissingBean
  OntModel ontModel(CortexProperties properties) throws IOException {
    return OntologyLoader.load(readAll(properties.ontologies()));
  }

  /**
   * Creates the service exposing the ontology.
   *
   * @param ontModel the ontology model
   * @return the ontology service
   */
  @Bean
  @ConditionalOnMissingBean
  OntologyService ontologyService(OntModel ontModel) {
    return new OntologyService(ontModel);
  }

  /**
   * Creates the SHACL validator.
   *
   * @return the validator
   */
  @Bean
  @ConditionalOnMissingBean
  ShaclValidator shaclValidator() {
    return ShaclValidator.get();
  }

  /**
   * Creates the SHACL shapes by reading the configured resources and delegating to {@link
   * ShapesLoader}.
   *
   * @param properties the Cortex configuration properties
   * @return the parsed, merged shapes
   * @throws IOException if a configured shapes resource cannot be read
   */
  @Bean
  @ConditionalOnMissingBean
  Shapes shapes(CortexProperties properties) throws IOException {
    return ShapesLoader.load(readAll(properties.shapes()));
  }

  /**
   * Creates the service linting assertions against the ontology and validating them against the
   * shapes.
   *
   * @param ontModel the ontology model
   * @param shaclValidator the SHACL validator
   * @param shapes the SHACL shapes
   * @return the lint service
   */
  @Bean
  @ConditionalOnMissingBean
  LintService lintService(OntModel ontModel, ShaclValidator shaclValidator, Shapes shapes) {
    return new LintService(ontModel, shaclValidator, shapes);
  }

  /**
   * Creates the dataset holding the approved assertions and the staged branches: an in-memory TDB2
   * dataset, or a persistent one at {@link CortexProperties#assertionsLocation()} when {@link
   * CortexProperties#persistent()} is set, seeded with the ontology's prefixes by delegating to
   * {@link AssertionStore}.
   *
   * <p>Matched by bean name ({@code "assertions"}), not by type — see the class-level Javadoc for
   * why a bare {@code @ConditionalOnMissingBean} on a {@link Dataset} return type would be unsafe
   * here.
   *
   * @param properties the Cortex configuration properties
   * @param ontModel the ontology model, whose prefixes seed the default model
   * @return the assertions dataset
   */
  @Bean
  @Qualifier("assertions")
  @ConditionalOnMissingBean(name = "assertions")
  Dataset assertions(CortexProperties properties, OntModel ontModel) {
    return AssertionStore.open(properties.persistent(), properties.assertionsLocation(), ontModel);
  }

  /**
   * Creates the recorder that builds and closes the provenance activity of an ingestion.
   *
   * @return the provenance recorder
   */
  @Bean
  @ConditionalOnMissingBean
  ProvenanceRecorder provenanceRecorder() {
    return new ProvenanceRecorder();
  }

  /**
   * Creates the repository backing branch existence checks and listing.
   *
   * @param assertions the assertions dataset
   * @return the branch repository
   */
  @Bean
  @ConditionalOnMissingBean
  BranchRepository branchRepository(@Qualifier("assertions") Dataset assertions) {
    return new BranchRepository(assertions);
  }

  /**
   * Creates the service validating and staging incoming assertions.
   *
   * @param assertions the assertions dataset
   * @param lintService the lint check and SHACL validation incoming assertions must pass
   * @param provenanceRecorder builds the provenance activity recorded when a branch is staged
   * @return the ingest service
   */
  @Bean
  @ConditionalOnMissingBean
  IngestService ingestService(
      @Qualifier("assertions") Dataset assertions,
      LintService lintService,
      ProvenanceRecorder provenanceRecorder) {
    return new IngestService(assertions, lintService, provenanceRecorder);
  }

  /**
   * Creates the service reading the assertions staged on a branch pending review.
   *
   * @param assertions the assertions dataset
   * @param ontModel the ontology model, used to abbreviate terms for display
   * @param branchRepository guards every read against an unknown branch or the provenance graph
   * @return the branch query service
   */
  @Bean
  @ConditionalOnMissingBean
  BranchQueryService branchQueryService(
      @Qualifier("assertions") Dataset assertions,
      OntModel ontModel,
      BranchRepository branchRepository) {
    return new BranchQueryService(assertions, ontModel, branchRepository);
  }

  /**
   * Creates the service applying reviewer edits to a branch pending review.
   *
   * @param assertions the assertions dataset
   * @param branchRepository guards edits against an unknown branch
   * @return the branch edit service
   */
  @Bean
  @ConditionalOnMissingBean
  BranchEditService branchEditService(
      @Qualifier("assertions") Dataset assertions, BranchRepository branchRepository) {
    return new BranchEditService(assertions, branchRepository);
  }

  /**
   * Creates the service approving or rejecting a branch pending review.
   *
   * @param assertions the assertions dataset
   * @param branchRepository guards resolution against an unknown branch
   * @param provenanceRecorder builds the provenance recorded when a branch is approved
   * @return the branch merge service
   */
  @Bean
  @ConditionalOnMissingBean
  BranchMergeService branchMergeService(
      @Qualifier("assertions") Dataset assertions,
      BranchRepository branchRepository,
      ProvenanceRecorder provenanceRecorder) {
    return new BranchMergeService(assertions, branchRepository, provenanceRecorder);
  }

  /**
   * Creates the service exporting the approved assertions.
   *
   * @param assertions the assertions dataset
   * @return the archive service
   */
  @Bean
  @ConditionalOnMissingBean
  ArchiveService archiveService(@Qualifier("assertions") Dataset assertions) {
    return new ArchiveService(assertions);
  }

  /**
   * Creates the inference dataset wrapped with a full-text index, by delegating to {@link
   * TextIndexFactory}.
   *
   * <p>Always in-memory: the inference closure is rebuilt from the approved assertions on every
   * startup regardless of {@code cortex.persistent}, so the text index wrapped around it is derived
   * data too, and persisting it would only buy disk I/O for something rebuilt anyway.
   *
   * <p>Matched by bean name ({@code "inferences"}), not by type — see the class-level Javadoc for
   * why a bare {@code @ConditionalOnMissingBean} on a {@link Dataset} return type would be unsafe
   * here.
   *
   * @return the indexed inference dataset
   */
  @Bean
  @Qualifier("inferences")
  @ConditionalOnMissingBean(name = "inferences")
  Dataset inferences() {
    return TextIndexFactory.open(DatasetFactory.createTxnMem());
  }

  /**
   * Creates the service answering SPARQL queries and full-text search.
   *
   * @param inferences the indexed inference dataset
   * @param assertions the assertions dataset, used to look up provenance
   * @param ontModel the ontology model, used to abbreviate terms for display
   * @return the query service
   */
  @Bean
  @ConditionalOnMissingBean
  QueryService queryService(
      @Qualifier("inferences") Dataset inferences,
      @Qualifier("assertions") Dataset assertions,
      OntModel ontModel) {
    return new QueryService(inferences, assertions, ontModel);
  }

  /**
   * Creates the rule reasoner by reading the configured resources and delegating to {@link
   * RuleLoader} and {@link ReasonerFactory}.
   *
   * @param properties the Cortex configuration properties
   * @return the configured reasoner, not yet bound to a schema
   * @throws IOException if a configured rules resource cannot be read
   */
  @Bean
  @ConditionalOnMissingBean
  GenericRuleReasoner genericRuleReasoner(CortexProperties properties) throws IOException {
    return ReasonerFactory.create(RuleLoader.load(readAll(properties.rules())));
  }

  /**
   * Creates the service applying rule-based inference to the approved assertions.
   *
   * @param assertions the assertions dataset
   * @param inferences the dataset the inference results are materialized into
   * @param genericRuleReasoner the rule reasoner, bound here to the ontology as schema
   * @param ontModel the ontology model, bound to the reasoner as schema
   * @return the inference service
   */
  @Bean
  @ConditionalOnMissingBean
  InferenceService inferenceService(
      @Qualifier("assertions") Dataset assertions,
      @Qualifier("inferences") Dataset inferences,
      GenericRuleReasoner genericRuleReasoner,
      OntModel ontModel) {
    Reasoner reasoner = genericRuleReasoner.bindSchema(ontModel);
    return new InferenceService(assertions, inferences, reasoner);
  }

  /**
   * Creates the initializer that computes inference once the application is ready.
   *
   * @param cortex the inference role used to recompute inference
   * @return the inference initializer
   */
  @Bean
  @ConditionalOnMissingBean
  InferenceInitializer inferenceInitializer(CortexInference cortex) {
    return new InferenceInitializer(cortex);
  }

  /**
   * Creates the service computing knowledge graph statistics over the datasets, ontology, shapes,
   * and rules.
   *
   * @param assertions the assertions dataset
   * @param inferences the indexed inference dataset
   * @param ontModel the ontology model
   * @param shapes the SHACL shapes
   * @param genericRuleReasoner the rule reasoner, whose loaded rules are counted
   * @return the stats service
   */
  @Bean
  @ConditionalOnMissingBean
  StatsService statsService(
      @Qualifier("assertions") Dataset assertions,
      @Qualifier("inferences") Dataset inferences,
      OntModel ontModel,
      Shapes shapes,
      GenericRuleReasoner genericRuleReasoner) {
    return new StatsService(
        assertions, inferences, ontModel, shapes, genericRuleReasoner.getRules());
  }

  /**
   * Creates the composed {@link Cortex} implementation from the eleven core services.
   *
   * @param ontologyService the ontology and its class hierarchy
   * @param lintService linting of assertions against the ontology
   * @param ingestService validated, branch-based ingestion
   * @param branchRepository branch existence and listing
   * @param branchQueryService reading the assertions staged on a branch
   * @param branchEditService reviewer edits to a branch
   * @param branchMergeService approving or rejecting a branch
   * @param archiveService export of the approved assertions
   * @param inferenceService rule-based inference over the approved assertions
   * @param queryService SPARQL queries and full-text search
   * @param statsService knowledge graph statistics
   * @return the composed Cortex implementation
   */
  @Bean
  @ConditionalOnMissingBean
  Cortex cortex(
      OntologyService ontologyService,
      LintService lintService,
      IngestService ingestService,
      BranchRepository branchRepository,
      BranchQueryService branchQueryService,
      BranchEditService branchEditService,
      BranchMergeService branchMergeService,
      ArchiveService archiveService,
      InferenceService inferenceService,
      QueryService queryService,
      StatsService statsService) {
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

  /**
   * Reads each resource as a UTF-8 string, in order.
   *
   * <p>The one place the ontology, shapes, and rules resources are turned into the {@code
   * List<String>} payload the {@code core} loaders accept — {@code cortex-core} must not depend on
   * Spring's {@link Resource}, so the conversion happens here, at the Spring boundary.
   *
   * @param resources the configured classpath or filesystem resources
   * @return the UTF-8 contents of each resource, in the same order
   * @throws IOException if a resource cannot be read
   */
  private static List<String> readAll(List<Resource> resources) throws IOException {
    List<String> contents = new ArrayList<>();
    for (Resource resource : resources) {
      contents.add(resource.getContentAsString(StandardCharsets.UTF_8));
    }
    return contents;
  }
}
