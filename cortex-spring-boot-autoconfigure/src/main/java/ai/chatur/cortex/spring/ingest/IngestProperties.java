package ai.chatur.cortex.spring.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "cortex.ingest")
public record IngestProperties(
    @DefaultValue(".cortex/db") String location,
    @DefaultValue("false") boolean persistent,
    @DefaultValue("classpath:shapes.ttl") Resource shapes) {}
