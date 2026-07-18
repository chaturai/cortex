package ai.chatur.cortex.core.usage;

import ai.chatur.cortex.core.CortexNamespace;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * ranking weight.
 *
 * <p>Only deliberate views are counted — opening a resource, not merely appearing in a result list.
 * Counting search impressions would boost whatever already ranked highly, which is self-fulfilling
 * and accelerates the feedback loop described below.
 *
 * <p>Counts live in the {@link CortexNamespace#USAGE usage graph} of the assertions dataset, a
 * reserved named graph alongside provenance. Writing there does not touch the default graph, so the
 * rule that approved assertions are only ever reached through review still holds.
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
   * How quickly the boost saturates: the view count at which half the maximum boost is reached.
   *
   * <p>Small, because the interesting distinction is between a resource nobody opens and one people
   * do — not between five hundred views and a thousand.
   */
  static final double SATURATION = 5.0;

  private final Dataset assertions;
  private final Map<String, Long> pending = new ConcurrentHashMap<>();
  private final AtomicInteger unflushed = new AtomicInteger();

  /**
   * Creates the service.
   *
   * @param assertions the dataset whose usage graph holds the counts
   */
  public UsageService(Dataset assertions) {
    this.assertions = assertions;
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
   * <p>Each count is re-read and incremented within that transaction, so concurrent writers and a
   * restore that replaced the dataset are both accounted for. Safe to call when nothing is pending.
   */
  public void flush() {
    Map<String, Long> batch = drain();
    if (batch.isEmpty()) {
      return;
    }
    try {
      Txn.executeWrite(
          assertions,
          () -> {
            Model usage = assertions.getNamedModel(CortexNamespace.USAGE.getURI());
            batch.forEach((uri, delta) -> increment(usage, uri, delta));
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
   * {@link #MAX_BOOST}.
   *
   * @param uris the resource URIs to weight
   * @return the weight per URI, {@code 1.0} for anything never viewed
   */
  public Map<String, Double> weights(Collection<String> uris) {
    if (uris.isEmpty()) {
      return Map.of();
    }
    Map<String, Double> weights = new HashMap<>();
    Txn.executeRead(
        assertions,
        () -> {
          Model usage = assertions.getNamedModel(CortexNamespace.USAGE.getURI());
          uris.forEach(
              uri -> weights.put(uri, weight(read(usage, uri) + pending.getOrDefault(uri, 0L))));
        });
    return weights;
  }

  /**
   * Returns how many times a resource has been viewed, including views not yet flushed.
   *
   * @param uri the resource URI
   * @return the view count, zero if it has never been viewed
   */
  public long viewCount(String uri) {
    long[] stored = {0};
    Txn.executeRead(
        assertions,
        () -> stored[0] = read(assertions.getNamedModel(CortexNamespace.USAGE.getURI()), uri));
    return stored[0] + pending.getOrDefault(uri, 0L);
  }

  /**
   * Converts a view count into a bounded multiplier.
   *
   * @param count the number of views
   * @return the multiplier, between {@code 1.0} and {@code 1 + MAX_BOOST}
   */
  static double weight(long count) {
    if (count <= 0) {
      return 1.0;
    }
    return 1.0 + MAX_BOOST * (count / (count + SATURATION));
  }

  private Map<String, Long> drain() {
    Map<String, Long> batch = new HashMap<>();
    // remove rather than copy-then-clear, so views recorded while the flush runs survive into the
    // next batch instead of being dropped
    pending.keySet().forEach(uri -> batch.put(uri, pending.remove(uri)));
    batch.values().removeIf(java.util.Objects::isNull);
    unflushed.set(0);
    return batch;
  }

  private static long read(Model usage, String uri) {
    Statement statement =
        usage.getProperty(ResourceFactory.createResource(uri), CortexNamespace.VIEW_COUNT);
    return statement == null ? 0 : statement.getLong();
  }

  private static void increment(Model usage, String uri, long delta) {
    Resource resource = ResourceFactory.createResource(uri);
    long updated = read(usage, uri) + delta;
    usage.removeAll(resource, CortexNamespace.VIEW_COUNT, null);
    usage.add(resource, CortexNamespace.VIEW_COUNT, usage.createTypedLiteral(updated));
  }
}
