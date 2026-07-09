package ai.chatur.cortex.query

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.query.enabled"])
@ComponentScan("cortex.query")
class QueryConfiguration
