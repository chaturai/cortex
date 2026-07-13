package ai.chatur.cortex.core;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Vocabulary of the <a href="https://www.w3.org/TR/prov-o/">W3C PROV-O</a> terms used to record
 * provenance in the knowledge graph.
 */
public final class PROV {

  /** The URI namespace of the PROV-O ontology. */
  public static final String NS = "http://www.w3.org/ns/prov#";

  /** The class of activities; here, the ingestion of a branch of assertions. */
  public static final Resource Activity = ResourceFactory.createResource(NS + "Activity");

  /** Links a generated entity — a reified statement — to the activity that generated it. */
  public static final Property wasGeneratedBy =
      ResourceFactory.createProperty(NS + "wasGeneratedBy");

  /** The time an activity started; here, when the branch was staged. */
  public static final Property startedAtTime = ResourceFactory.createProperty(NS + "startedAtTime");

  /** The time an activity ended; here, when the branch was approved. */
  public static final Property endedAtTime = ResourceFactory.createProperty(NS + "endedAtTime");

  private PROV() {}
}
