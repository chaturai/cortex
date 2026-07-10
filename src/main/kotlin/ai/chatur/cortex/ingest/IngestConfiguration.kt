package ai.chatur.cortex.ingest

import org.apache.jena.sparql.core.DatasetGraph
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.ingest.enabled"])
class IngestConfiguration {

  @Bean fun ingestRepository(dsg: DatasetGraph): IngestRepository = IngestRepository(dsg)

  @Bean fun ingestService(ingest: IngestRepository): IngestService = IngestService(ingest)
}
