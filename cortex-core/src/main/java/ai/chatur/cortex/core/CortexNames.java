package ai.chatur.cortex.core;

import java.util.UUID;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public final class CortexNames {

  public static final String NS = "cortex://";

  private CortexNames() {}

  public static Resource getResource(String name) {
    return ResourceFactory.createResource(NS + name);
  }

  public static Resource getResource() {
    UUID uuid = UUID.randomUUID();
    return getResource("branch-" + uuid);
  }
}
