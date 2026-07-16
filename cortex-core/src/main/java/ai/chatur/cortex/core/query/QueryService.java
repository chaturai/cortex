package ai.chatur.cortex.core.query;

import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.SearchResult;
import ai.chatur.cortex.Term;
import ai.chatur.cortex.core.CortexNamespace;
import ai.chatur.cortex.core.Terms;
import ai.chatur.cortex.core.jena.Rdf;
import ai.chatur.cortex.core.jena.Sparql;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

  private static final Query SEARCH_QUERY =
      QueryFactory.create(
          """
          PREFIX text: <http://jena.apache.org/text#>
          SELECT ?subject ?score ?match
          WHERE { (?subject ?score ?match) text:query ?text }
          ORDER BY DESC(?score)
          """);

  private final Dataset inferences;
  private final Dataset assertions;
  private final OntModel ontModel;

  /**
   * Creates the service.
   *
   * @param inferences the dataset holding the assertions enriched by inference
   * @param assertions the dataset holding the approved assertions and their provenance
   * @param ontModel the ontology model, used to resolve classes and shorten URIs
   */
  public QueryService(Dataset inferences, Dataset assertions, OntModel ontModel) {
    this.inferences = inferences;
    this.assertions = assertions;
    this.ontModel = ontModel;
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
   * <p>Each term of the input is matched approximately, so small typos and spelling variations
   * still find their target.
   *
   * @param text the text to search for
   * @return the matching subjects ranked best first, empty if nothing matches
   */
  public List<SearchResult> searchSubjects(String text) {
    Literal literal = ResourceFactory.createPlainLiteral(getFuzzyQuery(text));
    List<SearchResult> results = new ArrayList<>();
    Sparql.on(inferences, SEARCH_QUERY)
        .bind("text", literal)
        .forEachSolution(
            solution -> {
              Resource subject = solution.getResource("subject");
              if (subject.isURIResource()) {
                results.add(
                    new SearchResult(
                        Terms.of(subject, ontModel),
                        solution.contains("match")
                            ? solution.getLiteral("match").getLexicalForm()
                            : null));
              }
            });
    return results;
  }

  String getFuzzyQuery(String text) {
    return Arrays.stream(text.trim().split("\\s+"))
        .filter(term -> !term.isBlank())
        .map(QueryParserBase::escape)
        .map(term -> term + "~")
        .collect(Collectors.joining(" "));
  }
}
