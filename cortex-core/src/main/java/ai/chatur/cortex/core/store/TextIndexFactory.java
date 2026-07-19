package ai.chatur.cortex.core.store;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.vocabulary.RDFS;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a dataset with a Lucene full-text index over {@code rdfs:label} and {@code rdfs:comment},
 * held in separate fields so queries can rank a name above a description.
 *
 * <p>The index is always an in-memory {@link ByteBuffersDirectory}: it is derived data over the
 * approved assertions, rebuilt from scratch by rule-based inference on every startup, so persisting
 * it to disk would only buy I/O for something immediately discarded.
 */
public final class TextIndexFactory {

  private static final Logger log = LoggerFactory.getLogger(TextIndexFactory.class);

  // one analyzer for both indexing and querying: two different instances of the same analyzer
  // class tokenize identically today, but nothing enforces that, and a future divergence would
  // silently break search rather than fail loudly
  private static final Analyzer ANALYZER = new StandardAnalyzer();

  /** The indexed field holding {@code rdfs:label} values; also the default field for queries. */
  public static final String LABEL_FIELD = "label";

  private static final String COMMENT_FIELD = "comment";

  private TextIndexFactory() {}

  /**
   * Returns the analyzer used to tokenize indexed literals.
   *
   * <p>Callers building query strings <strong>must</strong> tokenize the user's input with this
   * same analyzer. Lucene's classic query parser resolves fuzzy and other multi-term queries
   * through {@link Analyzer#normalize}, which lower-cases but does <em>not</em> tokenize, so a
   * query term assembled by hand never matches an index term that the tokenizer split differently —
   * {@code note-pad} against an index holding {@code note} and {@code pad}, for instance. Sharing
   * one instance is what keeps query analysis and index analysis from drifting.
   *
   * @return the shared analyzer used for both indexing and querying
   */
  public static Analyzer analyzer() {
    return ANALYZER;
  }

  /**
   * Wraps the given dataset with an in-memory full-text index.
   *
   * @param dataset the dataset to index
   * @return the dataset wrapped with the full-text index
   */
  public static Dataset open(Dataset dataset) {
    // labels and comments are separate fields so a query can weight a name above a description;
    // rdf:type is deliberately absent, because indexing class URIs mixes their tokens into the same
    // relevance space as human-readable prose and gives every typed resource the same filler terms
    EntityDefinition entityDef = new EntityDefinition("uri", LABEL_FIELD, RDFS.label);
    entityDef.set(COMMENT_FIELD, RDFS.comment.asNode());
    // a uid per indexed triple lets the index delete documents when triples are removed, instead
    // of silently ignoring deletions and accumulating duplicates
    entityDef.setUidField("uid");

    TextIndexConfig indexConfig = new TextIndexConfig(entityDef);
    indexConfig.setAnalyzer(ANALYZER);
    indexConfig.setQueryAnalyzer(ANALYZER);
    indexConfig.setValueStored(true);

    Directory directory = new ByteBuffersDirectory();
    log.info("Using in-memory text index");
    return TextDatasetFactory.createLucene(dataset, directory, indexConfig);
  }
}
