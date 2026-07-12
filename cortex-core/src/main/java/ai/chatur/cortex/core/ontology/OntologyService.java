package ai.chatur.cortex.core.ontology;

import ai.chatur.cortex.OntologyClass;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public class OntologyService {

  private final OntModel ontModel;

  public OntologyService(OntModel ontModel) {
    this.ontModel = ontModel;
  }

  public String getOntology() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      RDFDataMgr.write(writer, ontModel, Lang.TTL);
    }
    return writer.toString();
  }

  public List<OntologyClass> getClassHierarchy() {
    return ontModel
        .hierarchyRoots()
        .filter(OntClass::isURIResource)
        .map(root -> getOntologyClass(root, new HashSet<>()))
        .sorted(Comparator.comparing(OntologyClass::name))
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
            .sorted(Comparator.comparing(OntologyClass::name))
            .toList();
    return new OntologyClass(ontClass.getLocalName(), subClasses);
  }
}
