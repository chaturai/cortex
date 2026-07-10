package ai.chatur.cortex.ingest

import java.io.OutputStream
import org.apache.jena.ontapi.model.OntModel
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr

class OntologyRepository(private val model: OntModel) {

  fun export(os: OutputStream) {
    RDFDataMgr.write(os, model, Lang.TTL)
  }
}
