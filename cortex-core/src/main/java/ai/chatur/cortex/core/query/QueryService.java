package ai.chatur.cortex.core.query;

import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.SearchResult;
import ai.chatur.cortex.Term;
import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.Terms;
import ai.chatur.cortex.core.jena.Rdf;
import ai.chatur.cortex.core.jena.Sparql;
import ai.chatur.cortex.core.store.TextIndexFactory;
import ai.chatur.cortex.core.usage.UsageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers questions about the knowledge graph.
 *
 * <p>All lookups run against the inference dataset, so results include both approved assertions and
 * statements derived from them by the reasoner. Provenance, which lives in the {@link
 * CortexNamespace#PROVENANCE provenance graph} of the assertions dataset and is excluded from
 * inference, is looked up there.
 */
public class QueryService {

  private static final Logger log = LoggerFactory.getLogger(QueryService.class);

  /** Tokens this short are matched exactly — two edits would reach most of the index. */
  private static final int SHORT_TOKEN = 3;

  /** Tokens up to this length allow one edit; longer ones allow two. */
  private static final int MEDIUM_TOKEN = 6;

  /** How much more a match in a resource's name counts than one in its description. */
  private static final int LABEL_BOOST = 3;

  /**
   * The most Lucene documents a search may retrieve.
   *
   * <p>The index holds one document per indexed literal, so this bounds documents rather than
   * subjects: a resource matching on both its label and its comment consumes two. Without a cap a
   * broad query walks the whole graph.
   */
  private static final int CANDIDATE_LIMIT = 200;

  /**
   * Searches labels and comments as separate branches, weighting names above descriptions.
   *
   * <p>Each branch names the property it searches. That is what makes {@code ?match} usable: the
   * text index resolves the property to its field and returns <em>that</em> field's stored literal,
   * whereas a field-qualified query string steers matching only and leaves retrieval pointed at the
   * default field, yielding a null match for every comment hit.
   */
  private static final Query SEARCH_QUERY =
      QueryFactory.create(
          """
          PREFIX text: <http://jena.apache.org/text#>
          PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          SELECT ?subject ?score ?match
          WHERE {
            {
              (?subject ?rawScore ?match) text:query (rdfs:label ?text %d) .
              BIND(?rawScore * %d AS ?score)
            }
            UNION
            {
              (?subject ?rawScore ?match) text:query (rdfs:comment ?text %d) .
              BIND(?rawScore AS ?score)
            }
          }
          ORDER BY DESC(?score)
          LIMIT %d
          """
              .formatted(CANDIDATE_LIMIT, LABEL_BOOST, CANDIDATE_LIMIT, CANDIDATE_LIMIT));

  private final Dataset inferences;
  private final Dataset assertions;
  private final OntModel ontModel;
  private final UsageService usageService;

  /**
   * Creates the service.
   *
   * @param inferences the dataset holding the assertions enriched by inference
   * @param assertions the dataset holding the approved assertions and their provenance
   * @param ontModel the ontology model, used to resolve classes and shorten URIs
   * @param usageService view counts, which weight the ranking of search results
   */
  public QueryService(
      Dataset inferences, Dataset assertions, OntModel ontModel, UsageService usageService) {
    this.inferences = inferences;
    this.assertions = assertions;
    this.ontModel = ontModel;
    this.usageService = usageService;
  }

  /**
   * Returns the known instances of an ontology class.
   *
   * @param type the local name of the ontology class
   * @return the instance identifiers sorted alphabetically, empty if the class is unknown
   */
  public List<Term> getInstances(String type) {
    return ontModel
        .classes()
        .filter(ontClass -> ontClass.getURI().equals(type))
        .findFirst()
        .map(this::listInstances)
        .orElseGet(
            () -> {
              log.warn("No instances for unknown ontology class {}", type);
              return List.of();
            });
  }

  List<Term> listInstances(OntClass ontClass) {
    Query query =
        QueryFactory.create(
            "SELECT DISTINCT ?instance WHERE { ?instance a <" + ontClass.getURI() + "> }");
    List<Term> instances = new ArrayList<>();
    Sparql.on(inferences, query)
        .forEachSolution(
            solution -> {
              Resource instance = solution.getResource("instance");
              if (instance.isURIResource()) {
                instances.add(Terms.of(instance, ontModel));
              }
            });
    return instances;
  }

  private static final Query DESCRIBE_QUERY =
      QueryFactory.create(
          """
          SELECT ?predicate ?object
          WHERE { ?subject ?predicate ?object }
          ORDER BY ?predicate ?object
          """);

  private static final Query PROVENANCE_QUERY =
      QueryFactory.create(
          """
          PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          PREFIX prov: <http://www.w3.org/ns/prov#>
          SELECT ?predicate ?object (MIN(?ended) AS ?created)
          WHERE {
            GRAPH <cortex://provenance> {
              ?reifier rdf:reifies <<( ?subject ?predicate ?object )>> .
              ?reifier prov:wasGeneratedBy ?activity .
              ?activity prov:endedAtTime ?ended .
            }
          }
          GROUP BY ?predicate ?object
          """);

  /**
   * Returns everything known about a resource, with the creation timestamp of each statement where
   * provenance was recorded.
   *
   * <p>The statements come from the inference dataset; their creation timestamps come from the
   * {@link CortexNamespace#PROVENANCE provenance graph} of the assertions dataset. Each statement
   * is returned once: statements carrying several provenance records — for example because they
   * were asserted by more than one ingestion — report their earliest creation timestamp.
   *
   * @param id the identifier of the resource within the Cortex namespace, or a full URI
   * @return the statements about the resource, sorted by predicate
   */
  public List<ProvenancedStatement> describe(String id) {
    // opening a resource is the deliberate-view signal that weights search ranking; recorded before
    // the reads below because a flush needs its own write transaction and must not nest
    usageService.recordView(id);
    Resource subject = ResourceFactory.createResource(id);
    Map<StatementKey, String> created = getCreated(subject);
    List<ProvenancedStatement> statements = new ArrayList<>();
    Sparql.on(inferences, DESCRIBE_QUERY)
        .bind("subject", subject)
        .forEachSolution(
            solution -> {
              RDFNode predicate = solution.get("predicate");
              RDFNode object = solution.get("object");
              statements.add(
                  new ProvenancedStatement(
                      Terms.of(predicate, ontModel),
                      Terms.of(object, ontModel),
                      created.get(new StatementKey(predicate, object))));
            });
    return statements;
  }

  Map<StatementKey, String> getCreated(Resource subject) {
    Map<StatementKey, String> created = new HashMap<>();
    Sparql.on(assertions, PROVENANCE_QUERY)
        .bind("subject", subject)
        .forEachSolution(
            solution ->
                created.put(
                    new StatementKey(solution.get("predicate"), solution.get("object")),
                    solution.getLiteral("created").getLexicalForm()));
    return created;
  }

  private record StatementKey(RDFNode predicate, RDFNode object) {}

  /**
   * Runs a SPARQL query against the knowledge graph.
   *
   * @param sparql a SPARQL {@code SELECT}, {@code ASK}, or {@code DESCRIBE} query
   * @return {@code SELECT} and {@code ASK} results formatted as text, {@code DESCRIBE} results
   *     serialized in Turtle syntax, or {@code null} for other query types
   */
  public String query(String sparql) {
    Query query = QueryFactory.create(sparql);
    return Sparql.on(inferences, query)
        .execute(
            queryExecution -> {
              if (query.isSelectType()) {
                ResultSet resultSet = queryExecution.execSelect();
                return ResultSetFormatter.asText(resultSet);
              }
              if (query.isAskType()) {
                return String.valueOf(queryExecution.execAsk());
              }
              if (query.isDescribeType()) {
                Model model = queryExecution.execDescribe();
                model.setNsPrefixes(ontModel.getNsPrefixMap());
                return Rdf.write(model, Lang.TTL);
              }
              return null;
            });
  }

  /**
   * Finds resources by fuzzy full-text search over their labels.
   *
   * <p>Each term of the input is matched approximately, so small typos and spelling variations
   * still find their target.
   *
   * @param text the text to search for
   * @return the matches with their relevance scores, formatted as text and ranked best first
   */
  public String search(String text) {
    Literal literal = ResourceFactory.createPlainLiteral(getFuzzyQuery(text));
    return Sparql.on(inferences, SEARCH_QUERY)
        .bind("text", literal)
        .execute(queryExecution -> ResultSetFormatter.asText(queryExecution.execSelect()));
  }

  /**
   * Searches the full-text index and returns the matching subjects.
   *
   * <p>Longer terms are matched approximately, so small typos and spelling variations still find
   * their target. Every term must occur in the same indexed literal, so adding a word narrows the
   * results.
   *
   * <p>The index holds one document per literal, so a resource matching on both its label and its
   * comment produces several hits; each resource is reported once, keeping its best-scoring match.
   *
   * @param text the text to search for
   * @return the matching subjects ranked best first, empty if nothing matches
   */
  public List<SearchResult> searchSubjects(String text) {
    Literal literal = ResourceFactory.createPlainLiteral(getFuzzyQuery(text));
    // insertion-ordered, and the query is already sorted by descending score, so keeping the first
    // hit per subject both de-duplicates and preserves the ranking
    Map<String, SearchResult> best = new LinkedHashMap<>();
    Sparql.on(inferences, SEARCH_QUERY)
        .bind("text", literal)
        .forEachSolution(
            solution -> {
              Resource subject = solution.getResource("subject");
              if (subject.isURIResource()) {
                best.computeIfAbsent(
                    subject.getURI(),
                    uri ->
                        new SearchResult(
                            Terms.of(subject, ontModel),
                            solution.contains("match")
                                ? solution.getLiteral("match").getLexicalForm()
                                : null,
                            solution.contains("score")
                                ? solution.getLiteral("score").getDouble()
                                : 0));
              }
            });
    return rankByPopularity(best);
  }

  /**
   * Weights each candidate by how often it has been viewed and re-sorts.
   *
   * <p>Re-ranking happens over the whole candidate set rather than a truncated head: a resource
   * that text relevance alone placed near the bottom of the candidates can still be promoted, which
   * is the entire point. The weight is bounded, so popularity reorders results of comparable
   * textual relevance instead of overriding relevance outright.
   *
   * @param best the best-scoring hit per subject URI, in descending textual relevance
   * @return the hits ranked by weighted relevance, best first
   */
  private List<SearchResult> rankByPopularity(Map<String, SearchResult> best) {
    Map<String, Double> weights = usageService.weights(best.keySet());
    return best.entrySet().stream()
        .map(entry -> weighted(entry.getValue(), weights.getOrDefault(entry.getKey(), 1.0)))
        .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
        .toList();
  }

  private static SearchResult weighted(SearchResult result, double weight) {
    return new SearchResult(result.subject(), result.match(), result.score() * weight);
  }

  /**
   * Builds the Lucene query string for the given user input.
   *
   * <p>The input is tokenized with {@link TextIndexFactory#analyzer() the index's own analyzer}
   * rather than split on whitespace. Lucene's classic query parser resolves fuzzy terms through
   * {@code Analyzer.normalize}, which lower-cases but does not tokenize, so a hand-split term such
   * as {@code communication-api} would be looked up whole against an index whose tokenizer had
   * already split it into {@code communication} and {@code api} — and match nothing.
   *
   * <p>Every token is required, so adding a word narrows the result set rather than widening it.
   */
  String getFuzzyQuery(String text) {
    List<String> tokens = analyze(text);
    if (tokens.isEmpty()) {
      return "";
    }
    // every token is required, so all of them must occur in the one literal a document holds; the
    // query runs against the label and comment fields separately, either of which may satisfy it
    return tokens.stream()
        .map(token -> "+" + QueryParserBase.escape(token) + fuzziness(token))
        .collect(Collectors.joining(" "));
  }

  /**
   * Tokenizes the input exactly as the indexer tokenized the literals being searched.
   *
   * @param text the raw user input
   * @return the analyzed tokens, empty if the input holds nothing searchable
   */
  private static List<String> analyze(String text) {
    List<String> tokens = new ArrayList<>();
    try (TokenStream stream =
        TextIndexFactory.analyzer().tokenStream(TextIndexFactory.LABEL_FIELD, text)) {
      CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
      stream.reset();
      while (stream.incrementToken()) {
        tokens.add(term.toString());
      }
      stream.end();
    } catch (IOException e) {
      // tokenizing an in-memory string cannot perform I/O; the checked exception is an artifact of
      // Lucene's Reader-based API
      throw new UncheckedIOException("Failed to analyze search text", e);
    }
    return tokens;
  }

  /**
   * Grades edit distance by token length.
   *
   * <p>A bare {@code ~} is edit distance 2, which on a short token matches a large share of the
   * index — {@code api} would reach every three-to-five character term. Short tokens are therefore
   * matched exactly, and only longer ones, where two edits are a small fraction of the token, get
   * the full allowance.
   *
   * @param token the analyzed token
   * @return the fuzziness suffix to append, empty for tokens too short to match approximately
   */
  private static String fuzziness(String token) {
    if (token.length() <= SHORT_TOKEN) {
      return "";
    }
    return token.length() <= MEDIUM_TOKEN ? "~1" : "~2";
  }
}
