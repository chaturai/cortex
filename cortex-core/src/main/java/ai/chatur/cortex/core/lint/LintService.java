package ai.chatur.cortex.core.lint;

import ai.chatur.cortex.LintResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lints assertions against the ontology and validates them against SHACL shapes before they are
 * ingested.
 *
 * <p>Every property used must be declared in the ontology, and every {@code rdf:type} object must
 * be a class declared in the ontology. The only terms permitted beyond the ontology are {@code
 * rdf:type}, {@code rdfs:label}, and {@code rdfs:comment}. Assertions that pass are returned
 * re-serialized in Turtle syntax, ready for ingestion.
 */
public class LintService {

  private static final Logger log = LoggerFactory.getLogger(LintService.class);

  /** Predicates permitted even though they are not declared in the ontology. */
  private static final Set<Property> ALLOWED_PREDICATES =
      Set.of(RDF.type, RDFS.label, RDFS.comment);

  private static final Set<Resource> PROPERTY_TYPES =
      Set.of(RDF.Property, OWL2.ObjectProperty, OWL2.DatatypeProperty, OWL2.AnnotationProperty);

  private static final Set<Resource> CLASS_TYPES = Set.of(RDFS.Class, OWL2.Class);

  private final Set<String> properties;
  private final Set<String> classes;
  private final ShaclValidator shaclValidator;
  private final Shapes shapes;

  /**
   * Creates the service, indexing the classes and properties declared in the ontology.
   *
   * @param ontModel the ontology model
   * @param shaclValidator the validator applied to incoming assertions
   * @param shapes the SHACL shapes incoming assertions must conform to
   */
  public LintService(OntModel ontModel, ShaclValidator shaclValidator, Shapes shapes) {
    this.properties = getDeclarations(ontModel, PROPERTY_TYPES);
    this.classes = getDeclarations(ontModel, CLASS_TYPES);
    this.shaclValidator = shaclValidator;
    this.shapes = shapes;
  }

  /**
   * Validates the given model against the configured SHACL shapes.
   *
   * @param model the model to validate
   * @return the validation report
   */
  public ValidationReport validate(Model model) {
    return shaclValidator.validate(shapes, model.getGraph());
  }

  /**
   * Renders the violations of a validation report.
   *
   * @param validationReport the report to render
   * @return the printed report, or {@code null} if the report conforms
   * @throws IOException if the report cannot be rendered
   */
  public String getErrors(ValidationReport validationReport) throws IOException {
    if (validationReport.conforms()) return null;
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      ShLib.printReport(os, validationReport);
      return os.toString();
    }
  }

  Set<String> getDeclarations(Model ontModel, Set<Resource> types) {
    Set<String> declarations = new LinkedHashSet<>();
    types.forEach(
        type ->
            ontModel
                .listStatements(null, RDF.type, type)
                .filterKeep(statement -> statement.getSubject().isURIResource())
                .forEach(statement -> declarations.add(statement.getSubject().getURI())));
    return declarations;
  }

  /**
   * Lints the given assertions against the ontology.
   *
   * <p>Assertions that cannot be parsed, use a property not declared in the ontology (other than
   * {@code rdf:type}, {@code rdfs:label}, or {@code rdfs:comment}), or type a resource with
   * anything but an ontology class are rejected; the violations are reported in the result instead.
   *
   * @param ttl RDF assertions in Turtle syntax
   * @return the outcome, carrying either the validated assertions in Turtle syntax or the lint
   *     violations
   * @throws IOException if the validated assertions cannot be serialized
   */
  public LintResult lint(String ttl) throws IOException {
    Model model = ModelFactory.createDefaultModel();
    try {
      RDFDataMgr.read(model, new StringReader(ttl), null, Lang.TTL);
    } catch (RiotException e) {
      log.warn("Rejected lint of malformed Turtle: {}", e.getMessage());
      return new LintResult(false, null, e.getMessage());
    }
    Set<String> violations = new LinkedHashSet<>();
    model
        .listStatements()
        .forEach(
            statement -> {
              Property predicate = statement.getPredicate();
              if (!ALLOWED_PREDICATES.contains(predicate)
                  && !properties.contains(predicate.getURI())) {
                violations.add("Property not found in ontology: " + predicate.getURI());
              }
              if (RDF.type.equals(predicate)) {
                RDFNode object = statement.getObject();
                if (!object.isURIResource() || !classes.contains(object.asResource().getURI())) {
                  violations.add("Class not found in ontology: " + object);
                }
              }
            });
    if (!violations.isEmpty()) {
      String errors = String.join("\n", violations);
      log.warn("Rejected lint of {} triples: {}", model.size(), errors);
      return new LintResult(false, null, errors);
    }
    StringWriter writer = new StringWriter();
    try (writer) {
      RDFDataMgr.write(writer, model, Lang.TTL);
    }
    return new LintResult(true, writer.toString(), null);
  }
}
