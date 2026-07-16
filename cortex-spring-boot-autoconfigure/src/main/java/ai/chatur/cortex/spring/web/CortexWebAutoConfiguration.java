package ai.chatur.cortex.spring.web;

import ai.chatur.cortex.CortexArchive;
import ai.chatur.cortex.CortexBranches;
import ai.chatur.cortex.CortexOntology;
import ai.chatur.cortex.CortexQuery;
import ai.chatur.cortex.CortexSearch;
import ai.chatur.cortex.CortexStatistics;
import ai.chatur.cortex.spring.CortexAutoConfiguration;
import ai.chatur.cortex.spring.archive.ArchiveController;
import ai.chatur.cortex.spring.branch.BranchController;
import ai.chatur.cortex.spring.branch.BranchEditController;
import ai.chatur.cortex.spring.graph.GraphController;
import ai.chatur.cortex.spring.ontology.OntologyController;
import ai.chatur.cortex.spring.query.SearchController;
import ai.chatur.cortex.spring.stats.StatsController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;

/**
 * Web UI auto-configuration for Cortex: the Thymeleaf controllers that browse the graph, review and
 * edit pending branches, and back up or restore the assertions dataset.
 *
 * <p>Runs {@link AutoConfiguration#after() after} {@link CortexAutoConfiguration}, so every
 * controller here can depend on the role interfaces the core beans satisfy.
 *
 * <p>Active only when all of the following hold, so a consumer wanting the graph without the UI
 * gets there with one property rather than by excluding individual controller beans:
 *
 * <ul>
 *   <li>{@code cortex.web.enabled} is not set to {@code false} (defaults to enabled)
 *   <li>the application is a servlet web application
 *   <li>Spring MVC's {@link Controller @Controller} is on the classpath
 * </ul>
 *
 * <p>Each controller is also {@link ConditionalOnMissingBean @ConditionalOnMissingBean}, so a
 * consumer may substitute their own by declaring a bean of the same type.
 */
@AutoConfiguration(after = CortexAutoConfiguration.class)
@ConditionalOnProperty(prefix = "cortex.web", name = "enabled", matchIfMissing = true)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(Controller.class)
public class CortexWebAutoConfiguration {

  /** Creates the auto-configuration. Spring instantiates this; consumers do not. */
  public CortexWebAutoConfiguration() {}

  /**
   * Creates the controller for browsing the knowledge graph: the class hierarchy, the instances of
   * a class, and everything known about a single resource.
   *
   * @param ontology the ontology role used to render the class hierarchy
   * @param query the query role used to list instances and describe a resource
   * @return the graph controller
   */
  @Bean
  @ConditionalOnMissingBean
  GraphController graphController(CortexOntology ontology, CortexQuery query) {
    return new GraphController(ontology, query);
  }

  /**
   * Creates the controller listing, viewing, approving, and rejecting pending branches.
   *
   * @param branches the branches role used to list, inspect, and resolve pending branches
   * @return the branch controller
   */
  @Bean
  @ConditionalOnMissingBean
  BranchController branchController(CortexBranches branches) {
    return new BranchController(branches);
  }

  /**
   * Creates the JSON API controller applying reviewer edits to a pending branch.
   *
   * @param branches the branches role used to apply edits to a pending branch
   * @return the branch edit controller
   */
  @Bean
  @ConditionalOnMissingBean
  BranchEditController branchEditController(CortexBranches branches) {
    return new BranchEditController(branches);
  }

  /**
   * Creates the controller for backing up and restoring the assertions dataset.
   *
   * @param archive the archive role used to export and restore the assertions dataset
   * @return the archive controller
   */
  @Bean
  @ConditionalOnMissingBean
  ArchiveController archiveController(CortexArchive archive) {
    return new ArchiveController(archive);
  }

  /**
   * Creates the web UI search controller.
   *
   * @param cortex the search role used to run full-text search
   * @return the search controller
   */
  @Bean
  @ConditionalOnMissingBean
  SearchController searchController(CortexSearch cortex) {
    return new SearchController(cortex);
  }

  /**
   * Creates the web UI controller for browsing the ontology.
   *
   * @param cortex the ontology role used to read the ontology and class hierarchy
   * @return the ontology controller
   */
  @Bean
  @ConditionalOnMissingBean
  OntologyController ontologyController(CortexOntology cortex) {
    return new OntologyController(cortex);
  }

  /**
   * Creates the web UI home page controller displaying knowledge graph statistics.
   *
   * @param cortex the statistics role used to compute knowledge graph statistics
   * @return the stats controller
   */
  @Bean
  @ConditionalOnMissingBean
  StatsController statsController(CortexStatistics cortex) {
    return new StatsController(cortex);
  }
}
