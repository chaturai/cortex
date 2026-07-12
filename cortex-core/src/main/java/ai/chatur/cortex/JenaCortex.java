package ai.chatur.cortex;

import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.apache.jena.vocabulary.DCTerms;

public class JenaCortex implements Cortex {
  Dataset assertions;
  Dataset inferences;
  OntModel ontModel;
  ShaclValidator shaclValidator;
  Shapes shapes;
  Reasoner reasoner;

  public JenaCortex(
      Dataset assertions,
      Dataset inferences,
      OntModel ontModel,
      ShaclValidator shaclValidator,
      Shapes shapes,
      Reasoner reasoner) {
    this.assertions = assertions;
    this.inferences = inferences;
    this.ontModel = ontModel;
    this.shaclValidator = shaclValidator;
    this.shapes = shapes;
    this.reasoner = reasoner;
  }

  public static String NS = "cortex://";

  Resource getResource(String branch) {
    return ResourceFactory.createResource(NS + branch);
  }

  Resource getResource() {
    UUID uuid = UUID.randomUUID();
    return getResource("branch-" + uuid);
  }

  Node getNode(String id) {
    return NodeFactory.createURI(NS + id);
  }

  ValidationReport validate(Model model) {
    return shaclValidator.validate(shapes, model.getGraph());
  }

  String getErrors(ValidationReport validationReport) throws IOException {
    if (validationReport.conforms()) return null;
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      ShLib.printReport(os, validationReport);
      return os.toString();
    }
  }

  @Override
  public String getOntology() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      RDFDataMgr.write(writer, ontModel, Lang.TTL);
    }
    return writer.toString();
  }

  @Override
  public IngestResult ingest(String ttl) throws IOException {
    StringReader reader = new StringReader(ttl);
    Resource namedModel = getResource();
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, reader, null, Lang.TTL);
    ValidationReport validationReport = validate(model);
    if (validationReport.conforms()) {
      Txn.executeWrite(assertions, () -> assertions.addNamedModel(namedModel, model));
      return new IngestResult(true, namedModel.getLocalName(), null);
    }
    return new IngestResult(false, null, getErrors(validationReport));
  }

  @Override
  public List<String> listBranches() {
    List<String> branches = new ArrayList<>();
    Txn.executeRead(
        assertions,
        () ->
            assertions
                .listModelNames()
                .forEachRemaining(
                    (node) -> {
                      branches.add(node.getLocalName());
                    }));
    return branches;
  }

  @Override
  public boolean hasBranch(String branch) {
    Resource namedModel = getResource(branch);
    return Txn.calculateRead(assertions, () -> assertions.containsNamedModel(namedModel));
  }

  @Override
  public String getBranch(String branch) throws IOException {
    Resource namedModel = getResource(branch);
    StringWriter writer = new StringWriter();
    try (writer) {
      Txn.executeRead(
          assertions,
          () -> {
            Model model = assertions.getNamedModel(namedModel);
            model.write(writer, "TTL");
          });
      return writer.toString();
    }
  }

  @Override
  public void approve(String branch) {
    if (hasBranch(branch)) {
      RDFChangesCollector collector = new RDFChangesCollector();
      Resource namedModel = getResource(branch);
      collector.txnBegin();
      Txn.executeRead(
          assertions,
          () ->
              getProvenanced(assertions.getNamedModel(namedModel)).getGraph().stream()
                  .forEach(
                      triple -> {
                        collector.add(
                            Quad.defaultGraphIRI,
                            triple.getSubject(),
                            triple.getPredicate(),
                            triple.getObject());
                      }));
      collector.txnCommit();
      RDFPatch patch = collector.getRDFPatch();
      RDFPatchOps.applyChange(assertions.asDatasetGraph(), patch);
      Txn.executeWrite(assertions, () -> assertions.removeNamedModel(namedModel));
      recomputeInference();
    }
  }

  Model getProvenanced(Model model) {
    Model provModel = ModelFactory.createDefaultModel();
    Literal now = provModel.createTypedLiteral(Calendar.getInstance());
    model
        .listStatements()
        .forEach(
            statement -> {
              provModel.add(statement);
              Resource quoted = provModel.createReifier(statement);
              provModel.add(quoted, DCTerms.created, now);
            });
    return provModel;
  }

  @Override
  public void recomputeInference() {
    Txn.executeWrite(
        inferences,
        () -> {
          Txn.executeRead(
              assertions,
              () -> {
                Model model = assertions.getDefaultModel();
                InfModel inf = ModelFactory.createInfModel(reasoner, model);
                inferences.setDefaultModel(inf);
              });
        });
  }

  @Override
  public void reject(String branch) {
    if (hasBranch(branch)) {
      Txn.calculateWrite(
          assertions,
          () -> {
            Resource namedModel = getResource(branch);
            assertions.removeNamedModel(namedModel);
            return true;
          });
    }
  }

  @Override
  public String getAssertions() throws IOException {
    StringWriter writer = new StringWriter();
    try (writer) {
      Txn.executeRead(
          assertions, () -> RDFDataMgr.write(writer, assertions.getDefaultModel(), Lang.TRIG));
    }
    return writer.toString();
  }

  @Override
  public List<OntologyClass> getClassHierarchy() {
    return ontModel
        .hierarchyRoots()
        .filter(OntClass::isURIResource)
        .map(root -> getOntologyClass(root, new HashSet<>()))
        .sorted(Comparator.comparing(OntologyClass::name))
        .toList();
  }

  OntologyClass getOntologyClass(OntClass ontClass, Set<OntClass> lineage) {
    Set<OntClass> path = new HashSet<>(lineage);
    path.add(ontClass);
    List<OntologyClass> subClasses =
        ontClass
            .subClasses(true)
            .filter(OntClass::isURIResource)
            .filter(subClass -> !path.contains(subClass))
            .map(subClass -> getOntologyClass(subClass, path))
            .sorted(Comparator.comparing(OntologyClass::name))
            .toList();
    return new OntologyClass(ontClass.getLocalName(), subClasses);
  }

  @Override
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
                      if (instance.isURIResource() && instance.getURI().startsWith(NS)) {
                        instances.add(instance.getURI().substring(NS.length()));
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

  @Override
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
    Resource subject = getResource(id);
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

  @Override
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
