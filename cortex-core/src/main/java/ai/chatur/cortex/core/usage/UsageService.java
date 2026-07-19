package ai.chatur.cortex.core.usage;

import ai.chatur.cortex.core.CortexNamespace;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counts how often each resource is deliberately viewed, and turns those counts into a search
 * ranking weight that fades as views age.
 *
 * <p>Only deliberate views are counted — opening a resource, not merely appearing in a result list.
 * Counting search impressions would boost whatever already ranked highly, which is self-fulfilling
 * and accelerates the feedback loop described below.
 *
 * <p>Counts live in the {@link CortexNamespace#USAGE usage graph} of the assertions dataset, a
 * reserved named graph alongside provenance. Writing there does not touch the default graph, so the
 * rule that approved assertions are only ever reached through review still holds.
 *
 * <p><strong>Views decay with age.</strong> The stored value is not a tally but a score halved
 * every {@code halfLife}, so what the graph's users consult *now* outranks what was popular a year
 * ago. Rather than keeping a timestamped event per view — which would grow without bound — each
 * resource keeps one score and the instant it was last brought up to date; the discount is applied
 * on read and folded in on write. Changing the half-life therefore takes effect immediately, with
 * no stored history to migrate. A half-life of zero disables decay, making the score a plain tally.
 *
 * <p><strong>Views are not written as they happen.</strong> TDB2 admits a single writer, so a write
 * transaction per view would serialize the read path against ingestion. Views accumulate in memory
 * and are flushed in batches; a crash loses at most {@link #FLUSH_THRESHOLD} views, which is
 * immaterial for a relevance signal.
 *
 * <p>Only the unflushed deltas are held in memory — never the totals. A restore replaces the whole
 * assertions dataset partway through startup, and a cached total captured before that would be
 * written back over the restored counts on the next flush. Reading the current value inside the
 * flush transaction makes the update a true increment and sidesteps the ordering entirely.
 */
public class UsageService {

  private static final Logger log = LoggerFactory.getLogger(UsageService.class);

  /** How many views may accumulate in memory before they are written out. */
  static final int FLUSH_THRESHOLD = 25;

  /**
   * The most popularity may multiply a relevance score.
   *
   * <p>Bounded deliberately. A resource viewed thousands of times would otherwise swamp textual
   * relevance entirely, and because boosted results are seen more often and so viewed more often,
   * an unbounded weight compounds into a rich-get-richer loop that new resources cannot break into.
   */
  static final double MAX_BOOST = 1.0;

  /**
   * How quickly the boost saturates: the score at which half the maximum boost is reached.
   *
   * <p>Small, because the interesting distinction is between a resource nobody opens and one people
   * do — not between five hundred views and a thousand.
   */
  static final double SATURATION = 5.0;

  private final Dataset assertions;
  private final Duration halfLife;
  private final Clock clock;
  private final Map<String, Long> pending = new ConcurrentHashMap<>();
  private final AtomicInteger unflushed = new AtomicInteger();

  /**
   * Creates the service using the system clock.
   *
   * @param assertions the dataset whose usage graph holds the counts
   * @param halfLife how long it takes a view's contribution to lose half its weight; zero or
   *     negative disables decay, making the score a plain tally
   */
  public UsageService(Dataset assertions, Duration halfLife) {
    this(assertions, halfLife, Clock.systemUTC());
  }

  /**
   * Creates the service with an explicit clock.
   *
   * @param assertions the dataset whose usage graph holds the counts
   * @param halfLife how long it takes a view's contribution to lose half its weight; zero or
   *     negative disables decay, making the score a plain tally
   * @param clock the source of the current time, which decay is measured against
   */
  public UsageService(Dataset assertions, Duration halfLife, Clock clock) {
    this.assertions = assertions;
    this.halfLife = halfLife == null ? Duration.ZERO : halfLife;
    this.clock = clock;
  }

  /**
   * Records that a resource was deliberately viewed.
   *
   * <p>Buffered in memory, and flushed once {@link #FLUSH_THRESHOLD} views have accumulated. Must
   * not be called from inside a transaction: a flush opens its own write transaction.
   *
   * @param uri the URI of the viewed resource
   */
  public void recordView(String uri) {
    if (uri == null || uri.isBlank()) {
      return;
    }
    pending.merge(uri, 1L, Long::sum);
    if (unflushed.incrementAndGet() >= FLUSH_THRESHOLD) {
      flush();
    }
  }

  /**
   * Writes the buffered views to the usage graph in a single write transaction.
   *
   * <p>Each score is re-read, decayed to the present, and incremented within that transaction, so
   * concurrent writers and a restore that replaced the dataset are both accounted for. Safe to call
   * when nothing is pending.
   */
  public void flush() {
    Map<String, Long> batch = drain();
    if (batch.isEmpty()) {
      return;
    }
    try {
      Instant now = clock.instant();
      Txn.executeWrite(
          assertions,
          () -> {
            Model usage = assertions.getNamedModel(CortexNamespace.USAGE.getURI());
            batch.forEach((uri, delta) -> increment(usage, uri, delta, now));
          });
    } catch (RuntimeException e) {
      // usage counts are a relevance signal, not data the caller asked us to store; losing a batch
      // must not fail the read that happened to trigger the flush
      log.warn("Failed to flush {} usage counts; they are lost", batch.size(), e);
    }
  }

  /**
   * Returns the ranking weight of each of the given resources.
   *
   * <p>The weight saturates, so popularity nudges the ranking rather than dictating it: a resource
   * nobody has opened scores {@code 1.0} and no resource, however popular, exceeds {@code 1 +}
   * {@link #MAX_BOOST}. Scores are decayed to the present before weighting, so a resource that was
   * popular long ago and has not been opened since ranks as though it were not.
   *
   * @param uris the resource URIs to weight
   * @return the weight per URI, {@code 1.0} for anything never viewed
   */
  public Map<String, Double> weights(Collection<String> uris) {
    if (uris.isEmpty()) {
      return Map.of();
    }
    Instant now = clock.instant();
    Map<String, Double> weights = new HashMap<>();
    Txn.executeRead(
        assertions,
        () -> {
          Model usage = assertions.getNamedModel(CortexNamespace.USAGE.getURI());
          uris.forEach(uri -> weights.put(uri, weight(score(usage, uri, now))));
        });
    return weights;
  }

  /**
   * Returns a resource's decayed view score, including views not yet flushed.
   *
   * <p>Equal to the number of views only when decay is disabled or no time has passed; otherwise
   * older views contribute less than newer ones.
   *
   * @param uri the resource URI
   * @return the decayed score, zero if the resource has never been viewed
   */
  public double viewScore(String uri) {
    Instant now = clock.instant();
    double[] stored = {0};
    Txn.executeRead(
        assertions,
        () ->
            stored[0] = score(assertions.getNamedModel(CortexNamespace.USAGE.getURI()), uri, now));
    return stored[0];
  }

  /**
   * Converts a decayed view score into a bounded multiplier.
   *
   * @param score the decayed view score
   * @return the multiplier, between {@code 1.0} and {@code 1 + MAX_BOOST}
   */
  static double weight(double score) {
    if (score <= 0) {
      return 1.0;
    }
    return 1.0 + MAX_BOOST * (score / (score + SATURATION));
  }

  /**
   * Discounts a score for the time elapsed since it was last updated.
   *
   * @param elapsed how long ago the score was current
   * @return the fraction of the score that survives, {@code 1.0} when decay is disabled
   */
  private double decay(Duration elapsed) {
    if (halfLife.isZero() || halfLife.isNegative() || elapsed.isNegative() || elapsed.isZero()) {
      return 1.0;
    }
    return Math.pow(0.5, (double) elapsed.toMillis() / halfLife.toMillis());
  }

  /** Reads a resource's stored score and brings it forward to {@code now}, adding pending views. */
  private double score(Model usage, String uri, Instant now) {
    Resource resource = ResourceFactory.createResource(uri);
    Statement stored = usage.getProperty(resource, CortexNamespace.VIEW_COUNT);
    double decayed =
        stored == null
            ? 0
            : stored.getDouble() * decay(Duration.between(updatedAt(usage, resource, now), now));
    return decayed + pending.getOrDefault(uri, 0L);
  }

  /**
   * Reads when a resource's score was last updated.
   *
   * <p>A missing timestamp is treated as the present rather than the epoch: counts written before
   * decay existed would otherwise be wiped out the first time they were read.
   */
  private static Instant updatedAt(Model usage, Resource resource, Instant now) {
    Statement statement = usage.getProperty(resource, CortexNamespace.VIEW_COUNT_UPDATED);
    if (statement == null) {
      return now;
    }
    try {
      return Instant.parse(statement.getString());
    } catch (RuntimeException e) {
      log.warn("Unreadable usage timestamp on {}; treating the score as current", resource, e);
      return now;
    }
  }

  private Map<String, Long> drain() {
    Map<String, Long> batch = new HashMap<>();
    // remove rather than copy-then-clear, so views recorded while the flush runs survive into the
    // next batch instead of being dropped
    pending.keySet().forEach(uri -> batch.put(uri, pending.remove(uri)));
    batch.values().removeIf(Objects::isNull);
    unflushed.set(0);
    return batch;
  }

  private void increment(Model usage, String uri, long delta, Instant now) {
    Resource resource = ResourceFactory.createResource(uri);
    Statement stored = usage.getProperty(resource, CortexNamespace.VIEW_COUNT);
    double decayed =
        stored == null
            ? 0
            : stored.getDouble() * decay(Duration.between(updatedAt(usage, resource, now), now));

    usage.removeAll(resource, CortexNamespace.VIEW_COUNT, null);
    usage.removeAll(resource, CortexNamespace.VIEW_COUNT_UPDATED, null);
    usage.add(resource, CortexNamespace.VIEW_COUNT, usage.createTypedLiteral(decayed + delta));
    usage.add(
        resource,
        CortexNamespace.VIEW_COUNT_UPDATED,
        usage.createTypedLiteral(now.toString(), XSDDatatype.XSDdateTime));
  }
}
