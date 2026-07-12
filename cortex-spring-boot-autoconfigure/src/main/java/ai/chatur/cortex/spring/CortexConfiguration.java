package ai.chatur.cortex.spring;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.JenaCortex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDFS;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "ai.chatur.cortex.spring")
@EnableConfigurationProperties(CortexProperties.class)
public class CortexConfiguration {

  @Bean
  @Qualifier("assertions")
  Dataset assertions(CortexProperties properties) {
    if (properties.persistent()) return TDB2Factory.connectDataset(properties.assertionsLocation());
    else return TDB2Factory.createDataset();
  }

  @Bean
  OntModel ontModel(CortexProperties properties) throws IOException {
    OntModel ontModel = OntModelFactory.createModel();
    ontModel.read(properties.ontology().getInputStream(), null, "TTL");
    ontModel.lock();
    return ontModel;
  }

  @Bean
  ShaclValidator shaclValidator() {
    return ShaclValidator.get();
  }

  @Bean
  Shapes shapes(CortexProperties properties) throws IOException {
    return Shapes.parse(properties.shapes().getFile().getAbsolutePath());
  }

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
  GenericRuleReasoner genericRuleReasoner(CortexProperties properties) throws IOException {
    List<Rule> rules =
        GenericRuleReasoner.loadRules(properties.rules().getFile().getAbsolutePath());
    GenericRuleReasoner genericRuleReasoner = new GenericRuleReasoner(rules);
    genericRuleReasoner.setOWLTranslation(true);
    genericRuleReasoner.setTransitiveClosureCaching(true);
    return genericRuleReasoner;
  }

  @Bean
  Cortex cortex(
      @Qualifier("assertions") Dataset assertions,
      @Qualifier("inferences") Dataset inferences,
      OntModel ontModel,
      ShaclValidator shaclValidator,
      Shapes shapes,
      GenericRuleReasoner genericRuleReasoner)
      throws IOException {
    Txn.executeWrite(
        assertions, () -> assertions.getDefaultModel().setNsPrefixes(ontModel.getNsPrefixMap()));
    Reasoner reasoner = genericRuleReasoner.bindSchema(ontModel);
    return new JenaCortex(assertions, inferences, ontModel, shaclValidator, shapes, reasoner);
  }
}
