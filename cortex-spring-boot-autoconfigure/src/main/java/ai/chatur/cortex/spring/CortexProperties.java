package ai.chatur.cortex.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for Cortex, bound to the {@code cortex.*} prefix.
 *
 * @param persistent whether assertions and the text index are persisted to disk; when {@code false}
 *     (the default) everything is held in memory
 * @param assertionsLocation the directory of the TDB2 store for assertions, used when persistent
 * @param indexLocation the directory of the Lucene full-text index, used when persistent
 * @param ontology the ontology the knowledge graph is built on, in Turtle syntax
 * @param shapes the SHACL shapes ingested assertions are validated against, in Turtle syntax
 * @param rules the Jena rules used for inference
 */
@ConfigurationProperties(prefix = "cortex")
public record CortexProperties(
    @DefaultValue("false") boolean persistent,
    @DefaultValue(".cortex/db") String assertionsLocation,
    @DefaultValue(".cortex/index") String indexLocation,
    @DefaultValue("classpath:ontology.ttl") Resource ontology,
    @DefaultValue("classpath:shapes.ttl") Resource shapes,
    @DefaultValue("classpath:ontology.rules") Resource rules) {}
