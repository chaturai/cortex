package ai.chatur.cortex.spring.query;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.core.query.QueryService;
import ai.chatur.cortex.spring.CortexProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures querying: the inference dataset wrapped with a Lucene full-text index over {@code
 * rdfs:label}, {@code rdfs:comment}, and {@code rdf:type} (in-memory or persistent, per the {@code
 * cortex.persistent} property), the {@link QueryService}, the web UI search controller, and the MCP
 * query and search tools.
 */
@Configuration
public class QueryConfiguration {

  private static final Logger log = LoggerFactory.getLogger(QueryConfiguration.class);

  @Bean
  @Qualifier("inferences")
  Dataset inferences(CortexProperties properties) {
    Dataset baseDataset = DatasetFactory.createTxnMem();
    EntityDefinition entityDef = new EntityDefinition("uri", "text", RDFS.label);
    entityDef.set("text", RDFS.comment.asNode());
    entityDef.set("text", RDF.type.asNode());
    // a uid per indexed triple lets the index delete documents when triples are removed, instead
    // of silently ignoring deletions and accumulating duplicates
    entityDef.setUidField("uid");

    Directory directory;
    if (properties.persistent()) {
      try {
        directory = FSDirectory.open(Path.of(properties.indexLocation()));
        // the inference dataset is in-memory and repopulated from the assertions on startup, so
        // documents left from the previous run would duplicate every re-indexed triple
        try (IndexWriter writer =
            new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
          writer.deleteAll();
        }
        log.info("Using persistent text index at {}", properties.indexLocation());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      directory = new ByteBuffersDirectory();
      log.info("Using in-memory text index");
    }

    TextIndexConfig indexConfig = new TextIndexConfig(entityDef);
    indexConfig.setQueryAnalyzer(new StandardAnalyzer());
    indexConfig.setValueStored(true);

    return TextDatasetFactory.createLucene(baseDataset, directory, indexConfig);
  }

  @Bean
  QueryService queryService(
      @Qualifier("inferences") Dataset inferences,
      @Qualifier("assertions") Dataset assertions,
      OntModel ontModel) {
    return new QueryService(inferences, assertions, ontModel);
  }

  @Bean
  QueryTools queryTools(Cortex cortex) {
    return new QueryTools(cortex);
  }

  @Bean
  SearchController searchController(Cortex cortex) {
    return new SearchController(cortex);
  }
}
