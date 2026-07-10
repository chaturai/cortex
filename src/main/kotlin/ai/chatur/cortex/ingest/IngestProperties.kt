package ai.chatur.cortex.ingest

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.core.io.Resource

@ConfigurationProperties(value = "cortex.ingest")
data class IngestProperties(
    @DefaultValue("true") val enabled: Boolean,
    @DefaultValue("classpath:ontologies") val ontologiesPath: Resource,
    @DefaultValue("[]") val ontologies: List<String>,
    @DefaultValue("classpath:shapes") val shapesPath: Resource,
    @DefaultValue("[]") val shapes: List<String>,
)
