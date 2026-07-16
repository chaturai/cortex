package ai.chatur.cortex.core.store;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a dataset with a Lucene full-text index over {@code rdfs:label}, {@code rdfs:comment}, and
 * {@code rdf:type}.
 *
 * <p>The index is always an in-memory {@link ByteBuffersDirectory}: it is derived data over the
 * approved assertions, rebuilt from scratch by rule-based inference on every startup, so persisting
 * it to disk would only buy I/O for something immediately discarded.
 */
public final class TextIndexFactory {

  private static final Logger log = LoggerFactory.getLogger(TextIndexFactory.class);

  private TextIndexFactory() {}

  /**
   * Wraps the given dataset with an in-memory full-text index.
   *
   * @param dataset the dataset to index
   * @return the dataset wrapped with the full-text index
   */
  public static Dataset open(Dataset dataset) {
    EntityDefinition entityDef = new EntityDefinition("uri", "text", RDFS.label);
    entityDef.set("text", RDFS.comment.asNode());
    entityDef.set("text", RDF.type.asNode());
    // a uid per indexed triple lets the index delete documents when triples are removed, instead
    // of silently ignoring deletions and accumulating duplicates
    entityDef.setUidField("uid");

    // one analyzer for both indexing and querying: two different instances of the same analyzer
    // class tokenize identically today, but nothing enforces that, and a future divergence would
    // silently break search rather than fail loudly
    StandardAnalyzer analyzer = new StandardAnalyzer();
    TextIndexConfig indexConfig = new TextIndexConfig(entityDef);
    indexConfig.setAnalyzer(analyzer);
    indexConfig.setQueryAnalyzer(analyzer);
    indexConfig.setValueStored(true);

    Directory directory = new ByteBuffersDirectory();
    log.info("Using in-memory text index");
    return TextDatasetFactory.createLucene(dataset, directory, indexConfig);
  }
}
