package ai.chatur.cortex.lint

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["cortex.lint.enabled"])
@ComponentScan("cortex.lint")
class LintConfiguration
