package ai.chatur.cortex;

import java.util.List;

/**
 * A node in the {@link Cortex#getClassHierarchy() class hierarchy} of the ontology.
 *
 * @param name the local name of the ontology class
 * @param subClasses the direct subclasses, sorted by name
 */
public record OntologyClass(String name, List<OntologyClass> subClasses) {}
