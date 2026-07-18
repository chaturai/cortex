package ai.chatur.cortex.usage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.usage.UsageService;
import java.util.List;
import java.util.Map;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior tests for view counting and the ranking weight derived from it. */
class UsageServiceTests {

  private static final String ALPHA = "example://kb/Alpha";
  private static final String BETA = "example://kb/Beta";

  private Dataset assertions;
  private UsageService usage;

  @BeforeEach
  void setUp() {
    assertions = DatasetFactory.createTxnMem();
    usage = new UsageService(assertions);
  }

  @Test
  void shouldCountViewsBeforeTheyAreFlushed() {
    usage.recordView(ALPHA);
    usage.recordView(ALPHA);

    assertThat(usage.viewCount(ALPHA))
        .as("buffered views count immediately, so ranking does not wait for a flush")
        .isEqualTo(2);
  }

  @Test
  void shouldPersistViewsToTheUsageGraphOnFlush() {
    usage.recordView(ALPHA);
    usage.recordView(ALPHA);
    usage.flush();

    Txn.executeRead(
        assertions,
        () ->
            assertThat(assertions.getNamedModel(CortexNamespace.USAGE.getURI()).size())
                .as("counts land in the reserved usage graph")
                .isEqualTo(1));
    assertThat(usage.viewCount(ALPHA)).isEqualTo(2);
  }

  @Test
  void shouldNotTouchTheDefaultGraph() {
    usage.recordView(ALPHA);
    usage.flush();

    Txn.executeRead(
        assertions,
        () ->
            assertThat(assertions.getDefaultModel().isEmpty())
                .as("usage is a reserved named graph; approved assertions are untouched")
                .isTrue());
  }

  @Test
  void shouldAccumulateAcrossFlushesRatherThanOverwrite() {
    usage.recordView(ALPHA);
    usage.flush();
    usage.recordView(ALPHA);
    usage.recordView(ALPHA);
    usage.flush();

    assertThat(usage.viewCount(ALPHA))
        .as("each flush increments the stored total instead of replacing it")
        .isEqualTo(3);
  }

  /**
   * A restore replaces the whole assertions dataset partway through startup. Because only unflushed
   * deltas are held in memory and the current value is re-read inside the flush transaction, counts
   * written by whoever produced the backup survive.
   */
  @Test
  void shouldIncrementOntoCountsThatAppearedAfterConstruction() {
    Txn.executeWrite(
        assertions,
        () ->
            assertions
                .getNamedModel(CortexNamespace.USAGE.getURI())
                .add(
                    assertions.getDefaultModel().createResource(ALPHA),
                    CortexNamespace.VIEW_COUNT,
                    assertions.getDefaultModel().createTypedLiteral(40L)));

    usage.recordView(ALPHA);
    usage.flush();

    assertThat(usage.viewCount(ALPHA))
        .as("a count restored after this service was constructed is added to, not clobbered")
        .isEqualTo(41);
  }

  @Test
  void shouldFlushAutomaticallyOnceEnoughViewsAccumulate() {
    for (int view = 0; view < 100; view++) {
      usage.recordView(ALPHA);
    }

    Txn.executeRead(
        assertions,
        () ->
            assertThat(assertions.getNamedModel(CortexNamespace.USAGE.getURI()).isEmpty())
                .as("views are written out in batches rather than one transaction per view")
                .isFalse());
  }

  @Test
  void shouldIgnoreBlankIdentifiers() {
    usage.recordView(null);
    usage.recordView("  ");
    usage.flush();

    Txn.executeRead(
        assertions,
        () ->
            assertThat(assertions.getNamedModel(CortexNamespace.USAGE.getURI()).isEmpty())
                .isTrue());
  }

  @Test
  void shouldWeightUnviewedResourcesNeutrally() {
    Map<String, Double> weights = usage.weights(List.of(ALPHA, BETA));

    assertThat(weights.get(ALPHA)).isEqualTo(1.0);
    assertThat(weights.get(BETA)).isEqualTo(1.0);
  }

  @Test
  void shouldWeightViewedResourcesAboveUnviewedOnes() {
    for (int view = 0; view < 10; view++) {
      usage.recordView(BETA);
    }

    Map<String, Double> weights = usage.weights(List.of(ALPHA, BETA));

    assertThat(weights.get(BETA)).isGreaterThan(weights.get(ALPHA));
  }

  @Test
  void shouldSaturateSoPopularityNeverSwampsRelevance() {
    seedCount(ALPHA, 10);
    seedCount(BETA, 1_000_000);

    Map<String, Double> weights = usage.weights(List.of(ALPHA, BETA));

    assertThat(weights.get(BETA))
        .as("the weight rises with views but is bounded, so nothing dominates on popularity alone")
        .isGreaterThan(weights.get(ALPHA))
        .isLessThanOrEqualTo(2.0);
    assertThat(weights.get(ALPHA)).isGreaterThan(1.0);
  }

  private void seedCount(String uri, long count) {
    Txn.executeWrite(
        assertions,
        () ->
            assertions
                .getNamedModel(CortexNamespace.USAGE.getURI())
                .add(
                    assertions.getDefaultModel().createResource(uri),
                    CortexNamespace.VIEW_COUNT,
                    assertions.getDefaultModel().createTypedLiteral(count)));
  }
}
