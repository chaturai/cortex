package ai.chatur.cortex.core

import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.springframework.core.io.Resource

fun getPaths(resource: Resource, filter: List<String>): List<String> {
  require(resource.isFile) { "$resource is not a file" }
  return resource.file.listFiles { filter.contains(it.name) }?.map { it.absolutePath }
      ?: emptyList()
}

fun Model.print() {
  RDFDataMgr.write(System.out, this, Lang.TTL)
}
