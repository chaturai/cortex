package ai.chatur.cortex;

import java.util.List;

/** Exposes the ontology a knowledge graph is built on, and its class hierarchy. */
public interface CortexOntology {

  /**
   * Returns the ontology that this knowledge graph is built on.
   *
   * @return the ontology serialized in Turtle syntax
   */
  String getOntology();

  /**
   * Returns the class hierarchy of the ontology.
   *
   * @return the root classes, each carrying its subclasses, sorted by name
   */
  List<OntologyClass> getClassHierarchy();
}
