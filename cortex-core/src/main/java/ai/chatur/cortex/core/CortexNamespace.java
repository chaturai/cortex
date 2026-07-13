package ai.chatur.cortex.core;

import java.util.UUID;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/** Creates resource names in the {@code cortex://} namespace used by the knowledge graph. */
public final class CortexNamespace {

  /** The URI namespace of all resources managed by Cortex. */
  public static final String NS = "cortex://";

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
