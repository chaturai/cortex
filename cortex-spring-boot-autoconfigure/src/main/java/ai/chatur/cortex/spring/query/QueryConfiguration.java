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
import org.apache.jena.vocabulary.RDFS;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryConfiguration {

  @Bean
  @Qualifier("inferences")
  Dataset inferences(CortexProperties properties) {
    Dataset baseDataset = DatasetFactory.createTxnMem();
    EntityDefinition entityDef = new EntityDefinition("uri", "text", RDFS.label);

    Directory directory;
    if (properties.persistent()) {
      try {
        directory = FSDirectory.open(Path.of(properties.indexLocation()));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      directory = new ByteBuffersDirectory();
    }

    TextIndexConfig indexConfig = new TextIndexConfig(entityDef);
    indexConfig.setQueryAnalyzer(new StandardAnalyzer());

    return TextDatasetFactory.createLucene(baseDataset, directory, indexConfig);
  }

  @Bean
  QueryService queryService(@Qualifier("inferences") Dataset inferences, OntModel ontModel) {
    return new QueryService(inferences, ontModel);
  }

  @Bean
  QueryTools queryTools(Cortex cortex) {
    return new QueryTools(cortex);
  }
}
