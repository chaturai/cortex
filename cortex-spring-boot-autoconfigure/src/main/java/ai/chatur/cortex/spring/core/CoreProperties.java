package ai.chatur.cortex.spring.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cortex")
public record CoreProperties(
    @DefaultValue(".cortex") String location, @DefaultValue("false") boolean persistent) {}
