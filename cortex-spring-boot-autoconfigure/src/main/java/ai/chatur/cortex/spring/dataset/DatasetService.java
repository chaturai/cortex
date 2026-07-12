package ai.chatur.cortex.spring.dataset;

import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {
  @Autowired
  @Qualifier("assertions")
  Dataset assertions;

  public void printAssertions() {
    Txn.executeRead(assertions, () -> RDFDataMgr.write(System.out, assertions, Lang.TRIG));
  }
}
