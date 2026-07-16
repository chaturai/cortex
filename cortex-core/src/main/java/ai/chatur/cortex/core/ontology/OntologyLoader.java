package ai.chatur.cortex.core.ontology;

import java.io.StringReader;
import java.util.List;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the immutable {@link OntModel} the knowledge graph is built on. */
public final class OntologyLoader {

  private static final Logger log = LoggerFactory.getLogger(OntologyLoader.class);

  private OntologyLoader() {}

  /**
   * Builds the ontology model by merging the given Turtle documents, in order, then locks it
   * against further mutation.
   *
   * @param ontologies the ontology documents, in Turtle syntax
   * @return the merged, locked ontology model
   */
  public static OntModel load(List<String> ontologies) {
    OntModel ontModel = OntModelFactory.createModel();
    for (String ontology : ontologies) {
      ontModel.read(new StringReader(ontology), null, "TTL");
    }
    log.info("Loaded ontology from {} document(s)", ontologies.size());
    ontModel.lock();
    return ontModel;
  }
}
