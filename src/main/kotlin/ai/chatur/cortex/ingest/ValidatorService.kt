package ai.chatur.cortex.ingest

import org.apache.jena.graph.Graph
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.shacl.ValidationReport

class ValidatorService(private val validator: ShaclValidator, private val shapes: Shapes) {

  fun validate(graph: Graph): ValidationReport {
    return validator.validate(shapes, graph)
  }
}
