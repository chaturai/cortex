package ai.chatur.cortex.core.query;

import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.core.CortexNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.system.Txn;

public class QueryService {

  private final Dataset inferences;
  private final OntModel ontModel;

  public QueryService(Dataset inferences, OntModel ontModel) {
    this.inferences = inferences;
    this.ontModel = ontModel;
  }

  public List<String> getInstances(String type) {
    return ontModel
        .classes()
        .filter(ontClass -> ontClass.getLocalName().equals(type))
        .findFirst()
        .map(this::listInstances)
        .orElse(List.of());
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
            return null;
          }
        });
  }

  public String search(String text) {
    Query query =
        QueryFactory.create(
            """
            PREFIX text: <http://jena.apache.org/text#>
            SELECT ?subject ?score ?match
            WHERE { (?subject ?score ?match) text:query ?text }
            ORDER BY DESC(?score)
            """);
    Literal literal = ResourceFactory.createPlainLiteral(text);
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
}
