package ai.chatur.cortex.ingest

import ai.chatur.cortex.core.getPaths
import ai.chatur.cortex.reason.ReasonRepository
import org.apache.jena.ontapi.OntModelFactory
import org.apache.jena.ontapi.OntSpecification
import org.apache.jena.ontapi.model.OntModel
import org.apache.jena.query.Dataset
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.sparql.graph.GraphFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
  @Qualifier("ontology")
  fun ontModel(ingestProperties: IngestProperties): OntModel {
    val paths = getPaths(ingestProperties.ontologiesPath, ingestProperties.ontologies)
    return getOntModel(paths)
  }

  @Bean
  fun ontologyRepository(@Qualifier("ontology") model: OntModel): OntologyRepository {
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
      reasonRepository: ReasonRepository,
      validatorService: ValidatorService,
  ): IngestService =
      IngestService(ingestRepository, ontologyRepository, reasonRepository, validatorService)

  private fun getOntModel(paths: List<String>): OntModel {
    val model = OntModelFactory.createModel(OntSpecification.OWL2_FULL_MEM)
    paths.forEach { RDFDataMgr.read(model, it) }
    return model
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
