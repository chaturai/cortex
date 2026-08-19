package ai.chatur.cortex.core.store;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a dataset with a Lucene full-text index over the human-readable annotations of a resource —
 * {@code rdfs:label} and {@code rdfs:comment} plus the SKOS labelling and note properties — all
 * collapsed into a single {@code text} field.
 *
 * <p>The index is always an in-memory {@link ByteBuffersDirectory}: it is derived data over the
 * approved assertions, rebuilt from scratch by rule-based inference on every startup, so persisting
 * it to disk would only buy I/O for something immediately discarded.
 */
public final class TextIndexFactory {

  private static final Logger log = LoggerFactory.getLogger(TextIndexFactory.class);

  // one analyzer for both indexing and querying: two different instances of the same analyzer
  // class tokenize identically today, but nothing enforces that, and a future divergence would
  // silently break search rather than fail loudly. EnglishAnalyzer stems and drops English stop
  // words, so recall comes from stemming (report/reports) rather than edit-distance fuzz.
  private static final Analyzer ANALYZER = new EnglishAnalyzer();

  /**
   * The single indexed field holding every annotation literal; also the default field for queries.
   */
  public static final String TEXT_FIELD = "text";

  // jena-text re-parses the query string with the parser named here. It matches this value
  // case-sensitively against "SurroundQueryParser", "ComplexPhraseQueryParser", and (deprecated)
  // "AnalyzingQueryParser", defaulting everything else to Lucene's standard classic QueryParser.
  // ComplexPhrase is a strict superset of the standard classic parser, so it still parses the
  // +term / "phrase" syntax QueryService.buildQuery emits, and additionally allows wildcards and
  // fuzzy terms inside quoted phrases. Surround's proximity grammar would reject our queries.
  private static final String PARSER = "ComplexPhraseQueryParser";

  private TextIndexFactory() {}

  /**
   * Returns the analyzer used to tokenize indexed literals.
   *
   * <p>Callers building query strings <strong>must</strong> tokenize the user's input with this
   * same analyzer — the query parser in {@code QueryService} is constructed with it for exactly
   * that reason. Sharing one instance is what keeps query analysis and index analysis from
   * drifting: a term the tokenizer split (or stemmed) one way at index time must be looked up the
   * same way, or it silently matches nothing.
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
    // every human-readable annotation goes into one field so a search reads the same way regardless
    // of which property carried the text; rdf:type is deliberately absent, because indexing class
    // URIs mixes their tokens into the same relevance space as prose and gives every typed resource
    // the same filler terms
    EntityDefinition entityDef = new EntityDefinition("uri", TEXT_FIELD, RDFS.label.asNode());
    entityDef.set(TEXT_FIELD, RDFS.comment.asNode());
    entityDef.set(TEXT_FIELD, SKOS.prefLabel.asNode());
    entityDef.set(TEXT_FIELD, SKOS.altLabel.asNode());
    entityDef.set(TEXT_FIELD, SKOS.hiddenLabel.asNode());
    entityDef.set(TEXT_FIELD, SKOS.definition.asNode());
    entityDef.set(TEXT_FIELD, SKOS.note.asNode());
    entityDef.set(TEXT_FIELD, SKOS.scopeNote.asNode());
    entityDef.set(TEXT_FIELD, SKOS.editorialNote.asNode());
    entityDef.set(TEXT_FIELD, SKOS.changeNote.asNode());
    entityDef.set(TEXT_FIELD, SKOS.historyNote.asNode());
    entityDef.set(TEXT_FIELD, SKOS.example.asNode());
    // a uid per indexed triple lets the index delete documents when triples are removed, instead
    // of silently ignoring deletions and accumulating duplicates
    entityDef.setUidField("uid");
    entityDef.setLangField("lang");

    TextIndexConfig indexConfig = new TextIndexConfig(entityDef);
    indexConfig.setAnalyzer(ANALYZER);
    indexConfig.setQueryAnalyzer(ANALYZER);
    indexConfig.setQueryParser(PARSER);
    indexConfig.setValueStored(true);

    Directory directory = new ByteBuffersDirectory();
    log.info("Using in-memory text index");
    return TextDatasetFactory.createLucene(dataset, directory, indexConfig);
  }
}
