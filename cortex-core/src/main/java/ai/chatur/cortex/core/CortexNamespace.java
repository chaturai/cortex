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

  /** The property recording how many times a resource has been viewed, within {@link #USAGE}. */
  public static final Property VIEW_COUNT = ResourceFactory.createProperty(NS + "viewCount");

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
    return getResource("branch-" + uuid);
  }
}
