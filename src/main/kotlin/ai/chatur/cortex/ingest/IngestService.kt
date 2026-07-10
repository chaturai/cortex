package ai.chatur.cortex.ingest

import java.io.ByteArrayOutputStream
import org.apache.jena.graph.Graph
import org.apache.jena.shacl.ValidationReport
import org.apache.jena.shacl.lib.ShLib

class IngestService(
    private val ingestRepository: IngestRepository,
    private val ontologyRepository: OntologyRepository,
    private val validatorService: ValidatorService,
) {

  fun stage(graph: Graph): StageResponse {
    val validationReport = validatorService.validate(graph)
    if (validationReport.conforms()) {
      val graphName = ingestRepository.stage(graph)
      return StageResponse(valid = true, graphName = graphName.uri, errors = null)
    } else {
      return StageResponse(valid = false, graphName = null, errors = getErrors(validationReport))
    }
  }

  private fun getErrors(validationReport: ValidationReport): String? {
    if (validationReport.conforms()) return null
    val os = ByteArrayOutputStream()
    os.use { ShLib.printReport(it, validationReport) }
    return os.toString(Charsets.UTF_8)
  }
}

data class StageResponse(val valid: Boolean, val graphName: String?, val errors: String?)
