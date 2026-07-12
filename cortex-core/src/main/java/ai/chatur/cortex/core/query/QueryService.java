package ai.chatur.cortex.core.query;

import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.core.CortexNames;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers questions about the knowledge graph.
 *
 * <p>All lookups run against the inference dataset, so results include both approved assertions and
 * statements derived from them by the reasoner.
 */
public class QueryService {

  private static final Logger log = LoggerFactory.getLogger(QueryService.class);

  private final Dataset inferences;
  private final OntModel ontModel;

  /**
   * Creates the service.
   *
   * @param inferences the dataset holding the assertions enriched by inference
   * @param ontModel the ontology model, used to resolve classes and shorten URIs
   */
  public QueryService(Dataset inferences, OntModel ontModel) {
    this.inferences = inferences;
    this.ontModel = ontModel;
  }

  /**
   * Returns the known instances of an ontology class.
   *
   * @param type the local name of the ontology class
   * @return the instance identifiers sorted alphabetically, empty if the class is unknown
   */
  public List<String> getInstances(String type) {
    return ontModel
        .classes()
        .filter(ontClass -> ontClass.getLocalName().equals(type))
        .findFirst()
        .map(this::listInstances)
        .orElseGet(
            () -> {
              log.warn("No instances for unknown ontology class {}", type);
              return List.of();
            });
  }

  List<String> listInstances(OntClass ontClass) {
    Query query =
        QueryFactory.create(
            "SELECT DISTINCT ?instance WHERE { ?instance a <" + ontClass.getURI() + "> }");
    return Txn.calculateRead(
        inferences,
        () -> {
          QueryExecution queryExecution = getQueryExecution(query);
          try (queryExecution) {
            List<String> instances = new ArrayList<>();
            queryExecution
                .execSelect()
                .forEachRemaining(
                    solution -> {
                      Resource instance = solution.getResource("instance");
                      if (instance.isURIResource()
                          && instance.getURI().startsWith(CortexNames.NS)) {
                        instances.add(instance.getURI().substring(CortexNames.NS.length()));
                      }
                    });
            instances.sort(Comparator.naturalOrder());
            return instances;
          }
        });
  }

  QueryExecution getQueryExecution(Query query) {
    return QueryExecution.dataset(inferences).query(query).build();
  }

  /**
   * Returns everything known about a resource, with the creation timestamp of each statement where
   * provenance was recorded.
   *
   * @param id the identifier of the resource within the Cortex namespace
   * @return the statements about the resource, sorted by predicate
   */
  public List<ProvenancedStatement> describe(String id) {
    Query query =
        QueryFactory.create(
            """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX dcterms: <http://purl.org/dc/terms/>
            SELECT ?predicate ?object ?created
            WHERE {
              ?subject ?predicate ?object .
              OPTIONAL {
                ?reifier rdf:reifies <<( ?subject ?predicate ?object )>> .
                ?reifier dcterms:created ?created .
              }
            }
            ORDER BY ?predicate ?object
            """);
    Resource subject = CortexNames.getResource(id);
    return Txn.calculateRead(
        inferences,
        () -> {
          QueryExecution queryExecution =
              QueryExecution.dataset(inferences)
                  .query(query)
                  .substitution("subject", subject)
                  .build();
          try (queryExecution) {
            List<ProvenancedStatement> statements = new ArrayList<>();
            queryExecution
                .execSelect()
                .forEachRemaining(
                    solution ->
                        statements.add(
                            new ProvenancedStatement(
                                shortForm(solution.get("predicate")),
                                shortForm(solution.get("object")),
                                solution.contains("created")
                                    ? solution.getLiteral("created").getLexicalForm()
                                    : null)));
            return statements;
          }
        });
  }

  String shortForm(RDFNode node) {
    if (node.isURIResource()) return ontModel.shortForm(node.asResource().getURI());
    if (node.isLiteral()) return node.asLiteral().getLexicalForm();
    return node.toString();
  }

  /**
   * Runs a SPARQL query against the knowledge graph.
   *
   * @param sparql a SPARQL {@code SELECT}, {@code ASK}, or {@code DESCRIBE} query
   * @return {@code SELECT} and {@code ASK} results formatted as text, {@code DESCRIBE} results
   *     serialized in Turtle syntax, or {@code null} for other query types
   */
  public String query(String sparql) {
    Query query = QueryFactory.create(sparql);
    return Txn.calculateRead(
        inferences,
        () -> {
          QueryExecution queryExecution = getQueryExecution(query);
          try (queryExecution) {
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
              StringWriter writer = new StringWriter();
              RDFDataMgr.write(writer, model, Lang.TTL);
              return writer.toString();
            }
            return null;
          }
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
    Query query =
        QueryFactory.create(
            """
            PREFIX text: <http://jena.apache.org/text#>
            SELECT ?subject ?score ?match
            WHERE { (?subject ?score ?match) text:query ?text }
            ORDER BY DESC(?score)
            """);
    Literal literal = ResourceFactory.createPlainLiteral(getFuzzyQuery(text));
    return Txn.calculateRead(
        inferences,
        () -> {
          QueryExecution queryExecution =
              QueryExecution.dataset(inferences).query(query).substitution("text", literal).build();
          try (queryExecution) {
            ResultSet resultSet = queryExecution.execSelect();
            return ResultSetFormatter.asText(resultSet);
          }
        });
  }

  String getFuzzyQuery(String text) {
    return Arrays.stream(text.trim().split("\\s+"))
        .filter(term -> !term.isBlank())
        .map(QueryParserBase::escape)
        .map(term -> term + "~")
        .collect(Collectors.joining(" "));
  }
}
