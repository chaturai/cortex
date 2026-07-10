package ai.chatur.cortex.reason

import ai.chatur.cortex.core.getPaths
import org.apache.jena.ontapi.model.OntModel
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner
import org.apache.jena.reasoner.rulesys.Rule
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ReasonProperties::class)
@ConditionalOnProperty(value = ["cortex.reason.enabled"])
class ReasonConfiguration {

  @Bean
  @Qualifier("inference")
  fun infModel(): Model {
    return ModelFactory.createDefaultModel()
  }

  @Bean
  fun reasonRepository(
      dataset: Dataset,
      @Qualifier("ontology") ontModel: OntModel,
      @Qualifier("inference") infModel: Model,
      reasonProperties: ReasonProperties,
  ): ReasonRepository {
    val paths = getPaths(reasonProperties.rulesPath, reasonProperties.rules)
    val rules = getRules(paths)
    val genericRuleReasoner = GenericRuleReasoner(rules)
    genericRuleReasoner.setOWLTranslation(true)
    genericRuleReasoner.setTransitiveClosureCaching(true)
    genericRuleReasoner.setDerivationLogging(true)
    val reasoner = genericRuleReasoner.bindSchema(ontModel)
    return ReasonRepository(dataset, reasoner, infModel)
  }

  @Bean fun reasonService(repository: ReasonRepository): ReasonService = ReasonService(repository)

  private fun getRules(paths: List<String>): List<Rule> {
    return paths.flatMap { GenericRuleReasoner.loadRules(it) }
  }
}
