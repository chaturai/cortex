package ai.chatur.cortex.core;

import java.util.UUID;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/** Creates resource names in the {@code cortex://} namespace used by the knowledge graph. */
public final class CortexNamespace {

  /** The URI namespace of all resources managed by Cortex. */
  public static final String NS = "cortex://";

  /** The named graph holding per-statement provenance within the assertions dataset. */
  public static final Resource PROVENANCE = ResourceFactory.createResource(NS + "provenance");

  /**
   * The named graph holding per-resource view counts within the assertions dataset.
   *
   * <p>Like {@link #PROVENANCE} this is a reserved graph, not a branch and not part of the approved
   * assertions: it records how the graph is <em>used</em> rather than what it claims.
   */
  public static final Resource USAGE = ResourceFactory.createResource(NS + "usage");

  /**
   * The property recording a resource's time-decayed view score, within {@link #USAGE}.
   *
   * <p>Not a plain tally: the value is discounted towards zero as it ages, so it is only meaningful
   * alongside {@link #VIEW_COUNT_UPDATED}, which says when it was last brought up to date.
   */
  public static final Property VIEW_COUNT = ResourceFactory.createProperty(NS + "viewCount");

  /** The instant {@link #VIEW_COUNT} was last recomputed, within {@link #USAGE}. */
  public static final Property VIEW_COUNT_UPDATED =
      ResourceFactory.createProperty(NS + "viewCountUpdated");

  /** The local-name prefix of every branch graph. */
  private static final String BRANCH_PREFIX = "branch-";

  private CortexNamespace() {}

  /**
   * Returns the resource with the given name in the Cortex namespace.
   *
   * @param name the local name of the resource
   * @return the resource named {@code cortex://<name>}
   */
  public static Resource getResource(String name) {
    return ResourceFactory.createResource(NS + name);
  }

  /**
   * Returns a fresh, randomly named branch resource.
   *
   * @return a resource named {@code cortex://branch-<uuid>}
   */
  public static Resource getResource() {
    UUID uuid = UUID.randomUUID();
    return getResource(BRANCH_PREFIX + uuid);
  }

  /**
   * Returns the graph holding the statements a branch stages for <em>removal</em> from the approved
   * assertions.
   *
   * <p>It is a graph of its own rather than part of the branch graph because the branch graph is a
   * set of statements to add, and the two must not be confused with each other by any reader. Its
   * name deliberately does not start with {@code branch-}, so {@link #isBranch} does not mistake it
   * for a branch of its own.
   *
   * @param branch the branch resource, named {@code cortex://branch-<uuid>}
   * @return a resource named {@code cortex://retract-<uuid>}
   */
  public static Resource getRetractions(Resource branch) {
    return getResource("retract-" + branch.getURI().substring((NS + BRANCH_PREFIX).length()));
  }

  /**
   * Reports whether the named graph is a branch, by its name alone.
   *
   * <p>Branches are named graphs of the assertions dataset, but so are {@link #PROVENANCE}, {@link
   * #USAGE}, and the retraction graphs of {@link #getRetractions branches themselves} — none of
   * which is a branch, and every one of which would otherwise be listed as one and could be
   * approved into the default graph.
   *
   * @param graph the named graph resource
   * @return {@code true} if the graph is a branch
   */
  public static boolean isBranch(Resource graph) {
    return graph.isURIResource() && graph.getURI().startsWith(NS + BRANCH_PREFIX);
  }
}
