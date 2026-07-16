package ai.chatur.cortex.core.ontology;

import ai.chatur.cortex.OntologyClass;
import ai.chatur.cortex.Term;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

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
   * @throws IOException if the ontology cannot be serialized
   */
  public String getOntology() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      RDFDataMgr.write(writer, ontModel, Lang.TTL);
    }
    return writer.toString();
  }

  /**
   * Returns the class hierarchy of the ontology, guarding against cycles.
   *
   * @return the root classes, each carrying its subclasses, sorted by name
   */
  public List<OntologyClass> getClassHierarchy() {
    return ontModel
        .hierarchyRoots()
        .filter(OntClass::isURIResource)
        .map(root -> getOntologyClass(root, new HashSet<>()))
        .toList();
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
    return new OntologyClass(
        new Term(
            ontModel.getNsURIPrefix(ontClass.getNameSpace()),
            ontClass.getLocalName(),
            ontClass.getURI()),
        subClasses);
  }
}
