package ai.chatur.cortex.spring;

import java.util.List;
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
 * @param ontology the ontologies the knowledge graph is built on, in Turtle syntax, merged in order
 * @param shapes the SHACL shapes ingested assertions are validated against, in Turtle syntax,
 *     merged in order
 * @param rules the Jena rules used for inference, concatenated in order
 */
@ConfigurationProperties(prefix = "cortex")
public record CortexProperties(
    @DefaultValue("false") boolean persistent,
    @DefaultValue(".cortex/db") String assertionsLocation,
    @DefaultValue(".cortex/index") String indexLocation,
    @DefaultValue("classpath:ontology.ttl") List<Resource> ontology,
    @DefaultValue("classpath:shapes.ttl") List<Resource> shapes,
    @DefaultValue("classpath:ontology.rules") List<Resource> rules) {}
