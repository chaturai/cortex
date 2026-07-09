package ai.chatur.cortex.reason

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.reason.enabled"])
@ComponentScan("cortex.reason")
class ReasonConfiguration {}
