package ai.chatur.cortex.ingest

import org.apache.jena.query.Dataset
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.ingest.enabled"])
class IngestConfiguration {

  @Bean fun ingestRepository(dataset: Dataset): IngestRepository = IngestRepository(dataset)

  @Bean fun ingestService(repository: IngestRepository): IngestService = IngestService(repository)
}
