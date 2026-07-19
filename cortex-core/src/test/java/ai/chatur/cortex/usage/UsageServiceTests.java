package ai.chatur.cortex.usage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.usage.UsageService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  private Dataset assertions;
  private MutableClock clock;
  private UsageService usage;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(START);
    assertions = DatasetFactory.createTxnMem();
    usage = new UsageService(assertions, Duration.ofDays(30), clock);
  }

  @Test
  void shouldCountViewsBeforeTheyAreFlushed() {
    usage.recordView(ALPHA);
    usage.recordView(ALPHA);

    assertThat(usage.viewScore(ALPHA))
        .as("buffered views count immediately, so ranking does not wait for a flush")
        .isEqualTo(2.0);
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
                .as("the reserved usage graph holds the score and the instant it was computed")
                .isEqualTo(2));
    assertThat(usage.viewScore(ALPHA)).isEqualTo(2.0);
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

    assertThat(usage.viewScore(ALPHA))
        .as("each flush increments the stored total instead of replacing it")
        .isEqualTo(3.0);
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

    assertThat(usage.viewScore(ALPHA))
        .as("a count restored after this service was constructed is added to, not clobbered")
        .isEqualTo(41.0);
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

  @Test
  void shouldFadeViewsAsTheyAge() {
    for (int view = 0; view < 8; view++) {
      usage.recordView(ALPHA);
    }
    usage.flush();
    double fresh = usage.viewScore(ALPHA);

    clock.advance(Duration.ofDays(30));

    assertThat(usage.viewScore(ALPHA))
        .as("one half-life on, the same views count for half as much")
        .isCloseTo(fresh / 2, org.assertj.core.data.Offset.offset(0.01));
  }

  @Test
  void shouldRankRecentInterestAboveFadedInterest() {
    for (int view = 0; view < 10; view++) {
      usage.recordView(ALPHA);
    }
    usage.flush();

    clock.advance(Duration.ofDays(365));
    usage.recordView(BETA);
    usage.flush();

    Map<String, Double> weights = usage.weights(List.of(ALPHA, BETA));

    assertThat(weights.get(BETA))
        .as("a single recent view outweighs ten views from a year ago")
        .isGreaterThan(weights.get(ALPHA));
  }

  @Test
  void shouldDecayOnReadWithoutRequiringAWrite() {
    usage.recordView(ALPHA);
    usage.flush();
    double fresh = usage.viewScore(ALPHA);

    clock.advance(Duration.ofDays(120));

    assertThat(usage.viewScore(ALPHA))
        .as("a resource nobody has opened since must fade without waiting for another view")
        .isLessThan(fresh);
  }

  @Test
  void shouldNotDecayWhenHalfLifeIsZero() {
    UsageService undecayed = new UsageService(assertions, Duration.ZERO, clock);
    for (int view = 0; view < 4; view++) {
      undecayed.recordView(ALPHA);
    }
    undecayed.flush();

    clock.advance(Duration.ofDays(3650));

    assertThat(undecayed.viewScore(ALPHA))
        .as("a zero half-life turns the score back into a plain lifetime tally")
        .isEqualTo(4.0);
  }

  /**
   * Counts written before decay existed carry no timestamp. Treating that as the epoch would wipe
   * them out on first read, so a missing timestamp means "current".
   */
  @Test
  void shouldTreatCountsWithoutATimestampAsCurrent() {
    seedCount(ALPHA, 10);

    clock.advance(Duration.ofDays(365));

    assertThat(usage.viewScore(ALPHA))
        .as("an untimestamped legacy count is not silently decayed away")
        .isEqualTo(10.0);
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

  /** A clock the test moves by hand, so decay can be observed without waiting for it. */
  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration amount) {
      now = now.plus(amount);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
