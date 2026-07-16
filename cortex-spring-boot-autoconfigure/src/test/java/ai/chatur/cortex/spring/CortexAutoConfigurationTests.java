package ai.chatur.cortex.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.spring.archive.ArchiveController;
import ai.chatur.cortex.spring.branch.BranchController;
import ai.chatur.cortex.spring.branch.BranchEditController;
import ai.chatur.cortex.spring.graph.GraphController;
import ai.chatur.cortex.spring.ingest.IngestTools;
import ai.chatur.cortex.spring.lint.LintTools;
import ai.chatur.cortex.spring.mcp.CortexMcpAutoConfiguration;
import ai.chatur.cortex.spring.ontology.OntologyController;
import ai.chatur.cortex.spring.ontology.OntologyResources;
import ai.chatur.cortex.spring.query.QueryTools;
import ai.chatur.cortex.spring.query.SearchController;
import ai.chatur.cortex.spring.stats.StatsController;
import ai.chatur.cortex.spring.web.CortexWebAutoConfiguration;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * {@link ApplicationContextRunner} tests for the Phase 7 auto-configuration split: {@link
 * CortexAutoConfiguration}, {@link CortexWebAutoConfiguration}, and {@link
 * CortexMcpAutoConfiguration}.
 *
 * <p>This is the first test in the repository that asserts <em>configuration</em> — which beans get
 * registered under which conditions — rather than {@link Cortex} behavior. The default {@code
 * cortex.*} properties resolve against the {@code ontology.ttl}/{@code ontology.shapes}/{@code
 * ontology.rules} already on this module's test classpath (see {@code src/test/resources}), so no
 * property overrides are needed to get a context that loads successfully.
 */
class CortexAutoConfigurationTests {

  private final ApplicationContextRunner coreRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CortexAutoConfiguration.class));

  private final ApplicationContextRunner mcpRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CortexAutoConfiguration.class, CortexMcpAutoConfiguration.class));

  private final WebApplicationContextRunner webRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CortexAutoConfiguration.class, CortexWebAutoConfiguration.class));

  @Test
  void contextLoadsWithCortexBean() {
    coreRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(Cortex.class);
        });
  }

  @Test
  void backsOffForUserSuppliedOntModel() {
    OntModel custom = OntModelFactory.createModel();
    coreRunner
        .withBean(OntModel.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(OntModel.class);
              assertThat(context.getBean(OntModel.class)).isSameAs(custom);
            });
  }

  @Test
  void backsOffForUserSuppliedCortex() {
    Cortex custom = mock(Cortex.class);
    coreRunner
        .withBean(Cortex.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(Cortex.class);
              assertThat(context.getBean(Cortex.class)).isSameAs(custom);
            });
  }

  @Test
  void assertionsAndInferencesAreDistinctDatasetInstances() {
    coreRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          Dataset assertions = context.getBean("assertions", Dataset.class);
          Dataset inferences = context.getBean("inferences", Dataset.class);
          assertThat(assertions).isNotSameAs(inferences);
        });
  }

  @Test
  void webControllersRegisteredByDefault() {
    webRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(GraphController.class);
          assertThat(context).hasSingleBean(BranchController.class);
          assertThat(context).hasSingleBean(BranchEditController.class);
          assertThat(context).hasSingleBean(ArchiveController.class);
          assertThat(context).hasSingleBean(SearchController.class);
          assertThat(context).hasSingleBean(OntologyController.class);
          assertThat(context).hasSingleBean(StatsController.class);
        });
  }

  @Test
  void webControllersAbsentWhenDisabled() {
    webRunner
        .withPropertyValues("cortex.web.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(GraphController.class);
              assertThat(context).doesNotHaveBean(BranchController.class);
              assertThat(context).doesNotHaveBean(BranchEditController.class);
              assertThat(context).doesNotHaveBean(ArchiveController.class);
              assertThat(context).doesNotHaveBean(SearchController.class);
              assertThat(context).doesNotHaveBean(OntologyController.class);
              assertThat(context).doesNotHaveBean(StatsController.class);
            });
  }

  @Test
  void mcpToolsRegisteredByDefault() {
    mcpRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(LintTools.class);
          assertThat(context).hasSingleBean(IngestTools.class);
          assertThat(context).hasSingleBean(QueryTools.class);
          assertThat(context).hasSingleBean(OntologyResources.class);
        });
  }

  @Test
  void mcpToolsAbsentWhenDisabled() {
    mcpRunner
        .withPropertyValues("cortex.mcp.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(LintTools.class);
              assertThat(context).doesNotHaveBean(IngestTools.class);
              assertThat(context).doesNotHaveBean(QueryTools.class);
              assertThat(context).doesNotHaveBean(OntologyResources.class);
            });
  }
}
