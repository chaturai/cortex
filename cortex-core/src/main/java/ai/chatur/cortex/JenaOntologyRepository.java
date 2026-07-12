package ai.chatur.cortex;

import java.io.IOException;
import java.io.StringWriter;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public class JenaOntologyRepository implements OntologyRepository {
  OntModel ontModel;

  public JenaOntologyRepository(OntModel ontModel) {
    this.ontModel = ontModel;
  }

  @Override
  public String getOntology() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      RDFDataMgr.write(writer, ontModel, Lang.TTL);
    }
    return writer.toString();
  }
}
