package ai.chatur.cortex.core.query;

import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.SearchResult;
import ai.chatur.cortex.StatementOrigin;
import ai.chatur.cortex.Term;
import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.Terms;
import ai.chatur.cortex.core.jena.Rdf;
import ai.chatur.cortex.core.jena.Sparql;
import ai.chatur.cortex.core.store.TextIndexFactory;
import ai.chatur.cortex.core.usage.UsageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
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

  /**
   * The most Lucene documents a search may retrieve.
   *
   * <p>The index holds one document per indexed literal, so this bounds documents rather than
   * subjects: a resource matching on both its label and its comment consumes two. Without a cap a
   * broad query walks the whole graph.
   */
  private static final int CANDIDATE_LIMIT = 200;

  /**
   * Searches the single {@code text} field of the index, ranked by Lucene relevance.
   *
   * <p>Every annotation — labels, comments, and the SKOS notes — is indexed into one {@code text}
   * field, so the query does not distinguish a name match from a description match. The property
   * named in the {@code text:query} list only selects the field to search: it resolves to {@code
   * text}, and because that field holds the text of every mapped predicate, naming {@code
   * rdfs:label} searches all of them. Retrieval stays on that field, so {@code ?match} is the
   * matching literal for every hit. Popularity re-weights the results afterwards.
   */
  private static final Query SEARCH_QUERY =
      QueryFactory.create(
          """
          PREFIX text: <http://jena.apache.org/text#>
          PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          SELECT ?subject ?score ?match
          WHERE {
            (?subject ?score ?match) text:query (rdfs:label ?text %d) .
          }
          ORDER BY DESC(?score)
          LIMIT %d
          """
              .formatted(CANDIDATE_LIMIT, CANDIDATE_LIMIT));

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

  private static final Query INSTANCE_COUNTS =
      QueryFactory.create(
          """
          SELECT ?type (COUNT(DISTINCT ?instance) AS ?count)
          WHERE { ?instance a ?type }
          GROUP BY ?type
          """);

  /**
   * Counts the instances of every class over the inference dataset, so a resource typed only by a
   * subclass still counts towards the superclasses the reasoner derives for it.
   *
   * @return the number of distinct instances keyed by class URI; classes with no instances are
   *     absent
   */
  public Map<String, Long> countInstances() {
    Map<String, Long> counts = new HashMap<>();
    Sparql.on(inferences, INSTANCE_COUNTS)
        .forEachSolution(
            solution -> {
              Resource type = solution.getResource("type");
              if (type != null && type.isURIResource()) {
                counts.put(type.getURI(), solution.getLiteral("count").getLong());
              }
            });
    return counts;
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
   * Returns everything known about a resource, with the {@link StatementOrigin origin} of each
   * statement and the creation timestamp of those where provenance was recorded.
   *
   * <p>The statements come from the inference dataset; their creation timestamps come from the
   * {@link CortexNamespace#PROVENANCE provenance graph} of the assertions dataset. Each statement
   * is returned once: statements carrying several provenance records — for example because they
   * were asserted by more than one ingestion — report their earliest creation timestamp.
   *
   * <p>The inference dataset holds more than the reasoner's conclusions: the reasoner is bound to
   * the ontology as its schema, so the ontology's own axioms are materialized alongside them. Those
   * have no provenance either, so absence of a timestamp alone does not make a statement inferred —
   * the origin distinguishes them by looking the statement up in the ontology.
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
              String timestamp = created.get(new StatementKey(predicate, object));
              boolean literal = object.isLiteral();
              String language = literal ? object.asLiteral().getLanguage() : null;
              statements.add(
                  new ProvenancedStatement(
                      Terms.of(predicate, ontModel),
                      Terms.of(object, ontModel),
                      timestamp,
                      getOrigin(subject, predicate, object, timestamp),
                      literal,
                      literal ? object.asLiteral().getDatatypeURI() : null,
                      language == null || language.isEmpty() ? null : language));
            });
    return statements;
  }

  StatementOrigin getOrigin(Resource subject, RDFNode predicate, RDFNode object, String created) {
    if (created != null) return StatementOrigin.ASSERTED;
    if (predicate.isURIResource()
        && ontModel.contains(
            subject, ResourceFactory.createProperty(predicate.asResource().getURI()), object))
      return StatementOrigin.ONTOLOGY;
    return StatementOrigin.INFERRED;
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
   * Finds resources by full-text search over their labels, comments, and SKOS annotations.
   *
   * <p>Every term of the input is required and matched exactly after analysis, so recall comes from
   * the analyzer's stemming ({@code reports} finds {@code report}) rather than edit-distance fuzz.
   *
   * @param text the text to search for
   * @return the matches with their relevance scores, formatted as text and ranked best first
   */
  public String search(String text) {
    Literal literal = ResourceFactory.createPlainLiteral(buildQuery(text));
    return Sparql.on(inferences, SEARCH_QUERY)
        .bind("text", literal)
        .execute(queryExecution -> ResultSetFormatter.asText(queryExecution.execSelect()));
  }

  /**
   * Searches the full-text index and returns the matching subjects.
   *
   * <p>Every term is required and must occur in the same indexed literal, so adding a word narrows
   * the results. Terms are matched exactly after analysis; the analyzer's stemming is what lets
   * spelling variations of the same word still find their target.
   *
   * <p>The index holds one document per literal, so a resource matching on both its label and its
   * comment produces several hits; each resource is reported once, keeping its best-scoring match.
   *
   * @param text the text to search for
   * @return the matching subjects ranked best first, empty if nothing matches
   */
  public List<SearchResult> searchSubjects(String text) {
    Literal literal = ResourceFactory.createPlainLiteral(buildQuery(text));
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
   * <p>The input is escaped and parsed by a classic {@link QueryParser} configured with {@link
   * TextIndexFactory#analyzer() the index's own analyzer} and an implicit {@code AND}, so every
   * term is required and adding a word narrows the results. Escaping neutralizes Lucene query
   * syntax — notably a leading {@code -}, which would otherwise read as a prohibition — and the
   * analyzer inside the parser tokenizes a run like {@code note-pad} into a {@code "note pad"}
   * phrase, so the query matches the same index terms regardless of the separator the user typed.
   * The parsed query is rendered back to a default-field string for the {@code text:query} property
   * function to run.
   *
   * @param text the raw user input
   * @return the query string, empty if the input analyzes to nothing searchable
   */
  String buildQuery(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    QueryParser parser = new QueryParser(TextIndexFactory.TEXT_FIELD, TextIndexFactory.analyzer());
    parser.setDefaultOperator(QueryParser.Operator.AND);
    try {
      return parser.parse(QueryParser.escape(text)).toString(TextIndexFactory.TEXT_FIELD);
    } catch (ParseException e) {
      // the input is fully escaped before parsing, so it holds no query syntax that can fail to
      // parse; the checked exception is an artifact of the parser API
      throw new IllegalStateException("Failed to parse search text", e);
    }
  }
}
