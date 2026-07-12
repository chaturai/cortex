package ai.chatur.cortex.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "cortex")
public record CortexProperties(
    @DefaultValue("false") boolean persistent,
    @DefaultValue(".cortex/db") String assertionsLocation,
    @DefaultValue("classpath:ontology.ttl") Resource ontology,
    @DefaultValue("classpath:shapes.ttl") Resource shapes,
    @DefaultValue("classpath:ontology.rules") Resource rules) {}
