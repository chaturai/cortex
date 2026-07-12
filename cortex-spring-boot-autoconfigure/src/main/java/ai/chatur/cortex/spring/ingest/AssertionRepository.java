package ai.chatur.cortex.spring.ingest;

import java.io.OutputStream;
import java.util.Iterator;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AssertionRepository {
  @Autowired
  @Qualifier("assertions")
  Dataset assertions;

  public Iterator<Resource> getBranches() {
    return Txn.calculateRead(assertions, () -> assertions.listModelNames());
  }

  public boolean hasBranch(String uri) {
    return Txn.calculateRead(
        assertions, () -> assertions.containsNamedModel(ResourceFactory.createResource(uri)));
  }

  public void writeAssertions(OutputStream os) {
    Txn.executeRead(assertions, () -> RDFDataMgr.write(os, assertions, Lang.TRIG));
  }
}
