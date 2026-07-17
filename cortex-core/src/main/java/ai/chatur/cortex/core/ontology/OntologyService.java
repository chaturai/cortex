package ai.chatur.cortex.core.ontology;

import ai.chatur.cortex.OntologyClass;
import ai.chatur.cortex.core.Terms;
import ai.chatur.cortex.core.jena.Rdf;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.riot.Lang;

/** Provides access to the ontology the knowledge graph is built on. */
public class OntologyService {

  private final OntModel ontModel;

  /**
   * Creates the service.
   *
   * @param ontModel the ontology model
   */
  public OntologyService(OntModel ontModel) {
    this.ontModel = ontModel;
  }

  /**
   * Returns the ontology.
   *
   * @return the ontology serialized in Turtle syntax
   */
  public String getOntology() {
    return Rdf.write(ontModel, Lang.TTL);
  }

  /**
   * Returns the class hierarchy of the ontology, guarding against cycles.
   *
   * @return the root classes, each carrying its subclasses, sorted by name
   */
  public List<OntologyClass> getClassHierarchy() {
    return ontModel
        .classes()
        .filter(this::isRootClass)
        .map(root -> getOntologyClass(root, new HashSet<>()))
        .toList();
  }

  Boolean isRootClass(OntClass.Named ontClass) {
    return ontClass.isHierarchyRoot();
  }

  OntologyClass getOntologyClass(OntClass ontClass, Set<OntClass> lineage) {
    Set<OntClass> path = new HashSet<>(lineage);
    path.add(ontClass);
    List<OntologyClass> subClasses =
        ontClass
            .subClasses(true)
            .filter(OntClass::isURIResource)
            .filter(subClass -> !path.contains(subClass))
            .map(subClass -> getOntologyClass(subClass, path))
            .toList();
    return new OntologyClass(Terms.of(ontClass, ontModel), subClasses);
  }
}
