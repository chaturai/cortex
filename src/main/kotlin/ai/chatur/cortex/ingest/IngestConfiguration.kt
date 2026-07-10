package ai.chatur.cortex.ingest

import org.apache.jena.ontapi.OntModelFactory
import org.apache.jena.ontapi.OntSpecification
import org.apache.jena.ontapi.model.OntModel
import org.apache.jena.query.Dataset
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.sparql.graph.GraphFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
@EnableConfigurationProperties(IngestProperties::class)
@ConditionalOnProperty(value = ["cortex.ingest.enabled"])
class IngestConfiguration {

  @Bean
  fun ingestRepository(dataset: Dataset): IngestRepository {
    val datasetGraph = dataset.asDatasetGraph()
    return IngestRepository(datasetGraph)
  }

  @Bean
  fun ontologyRepository(ingestProperties: IngestProperties): OntologyRepository {
    val paths = getPaths(ingestProperties.ontologiesPath, ingestProperties.ontologies)
    val model = getOntModel(paths)
    return OntologyRepository(model)
  }

  @Bean
  fun validatorService(ingestProperties: IngestProperties): ValidatorService {
    val validator = getShaclValidator()
    val paths = getPaths(ingestProperties.shapesPath, ingestProperties.shapes)
    val shapes = getShapes(paths)
    return ValidatorService(validator, shapes)
  }

  @Bean
  fun ingestService(
      ingestRepository: IngestRepository,
      ontologyRepository: OntologyRepository,
      validatorService: ValidatorService,
  ): IngestService = IngestService(ingestRepository, ontologyRepository, validatorService)

  private fun getOntModel(paths: List<String>): OntModel {
    val model = OntModelFactory.createModel(OntSpecification.OWL2_FULL_MEM)
    paths.forEach { RDFDataMgr.read(model, it) }
    return model
  }

  private fun getPaths(resource: Resource, filter: List<String>): List<String> {
    require(resource.isFile) { "$resource is not a file" }
    return resource.file.listFiles { filter.contains(it.name) }?.map { it.absolutePath }
        ?: emptyList()
  }

  private fun getShaclValidator(): ShaclValidator {
    return ShaclValidator.get()
  }

  private fun getShapes(paths: List<String>): Shapes {
    val graph = GraphFactory.createDefaultGraph()
    paths.forEach { RDFDataMgr.read(graph, it) }
    return Shapes.parse(graph)
  }
}
