package ai.chatur.cortex;

import java.util.List;

/**
 * One node of the ontology's class hierarchy, as returned by {@link
 * CortexOntology#getClassHierarchy()}.
 *
 * @param type the term identifying this class
 * @param subClasses the classes declared as {@code rdfs:subClassOf} this one, sorted by name
 */
public record OntologyClass(Term type, List<OntologyClass> subClasses) {}
