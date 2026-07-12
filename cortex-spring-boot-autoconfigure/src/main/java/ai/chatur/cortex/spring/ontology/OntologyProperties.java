package ai.chatur.cortex.spring.ontology;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "cortex.ontology")
public record OntologyProperties(
    @DefaultValue("classpath:ontology.ttl") Resource path,
    @DefaultValue("cortex://ontology") String base,
    @DefaultValue("TTL") String format) {}
