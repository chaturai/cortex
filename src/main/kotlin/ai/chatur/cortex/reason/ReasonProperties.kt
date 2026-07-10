package ai.chatur.cortex.reason

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.core.io.Resource

@ConfigurationProperties(value = "cortex.reason")
data class ReasonProperties(
    @DefaultValue("true") val enabled: Boolean,
    @DefaultValue("classpath:rules") val rulesPath: Resource,
    @DefaultValue("[]") val rules: List<String>,
)
