package ai.chatur.cortex.core

import java.io.OutputStream
import org.apache.jena.query.Dataset
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.system.Txn

class DatasetService(private val ds: Dataset) {

  fun export(os: OutputStream) {
    Txn.executeRead(ds) { RDFDataMgr.write(os, ds, Lang.TRIG) }
  }
}
