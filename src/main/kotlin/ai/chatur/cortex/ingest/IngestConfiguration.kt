package ai.chatur.cortex.ingest

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.ingest.enabled"])
@ComponentScan("cortex.ingest")
class IngestConfiguration {}
