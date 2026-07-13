package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.OntologyClass;
import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.spring.CortexConfiguration;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@SpringJUnitConfig(CortexConfiguration.class)
public class IngestUnitTests {

  @Autowired
  @Qualifier("assertions")
  Dataset assertions;

  @Autowired Cortex cortex;

  @Autowired IngestController ingestController;

  @Autowired
  @Value("assertions/valid.ttl")
  Resource validAssertion;

  @Autowired
  @Value("assertions/invalid.ttl")
  Resource invalidAssertion;

  @Test
  void assertionsShouldUseTDB2() {
    assert (TDB2Factory.isTDB2(assertions));
  }

  @Test
  void shouldStageValidAssertions() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    IngestResult ingestResult = cortex.ingest(ttl);
    assert (ingestResult.valid());
    assert (cortex.hasBranch(ingestResult.branch()));
    assert (ingestResult.errors() == null);
  }

  @Test
  void controllerShouldRenderBranchesView() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    IngestResult ingestResult = cortex.ingest(ttl);

    Model model = new ExtendedModelMap();
    String view = ingestController.listBranches(model);
    assert (view.equals("branches"));

    @SuppressWarnings("unchecked")
    List<String> branches = (List<String>) model.getAttribute("branches");
    assert (branches != null);
    assert (branches.contains(ingestResult.branch()));
  }

  @Test
  void shouldNotStageInvalidAssertions() throws IOException {
    String ttl = invalidAssertion.getContentAsString(Charset.defaultCharset());
    IngestResult ingestResult = cortex.ingest(ttl);
    assert (!ingestResult.valid());
    assert (ingestResult.branch() == null);
    assert (ingestResult.errors() != null);
  }

  @Test
  void shouldNotStageAssertionsFailingLint() throws IOException {
    String ttl =
        """
        @prefix o: <cortex://ontology/> .
        @prefix : <cortex://assertions/> .

        :LintTask o:unknownProperty :LintAgent .
        """;
    IngestResult ingestResult = cortex.ingest(ttl);
    assert (!ingestResult.valid());
    assert (ingestResult.branch() == null);
    assert (ingestResult.errors().contains("cortex://ontology/unknownProperty"));
  }

  @Test
  void controllerShouldRenderBranchView() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    IngestResult ingestResult = cortex.ingest(ttl);
    String branch = ingestResult.branch();

    Model model = new ExtendedModelMap();
    String view = ingestController.getBranch(branch, model);
    assert (view.equals("assertions"));
  }

  @Test
  void controllerShouldRenderClassesView() {
    Model model = new ExtendedModelMap();
    String view = ingestController.getAssertions(null, model);
    assert (view.equals("classes"));

    @SuppressWarnings("unchecked")
    List<OntologyClass> classes = (List<OntologyClass>) model.getAttribute("classes");
    assert (classes != null);
    List<String> names = classes.stream().map(OntologyClass::name).toList();
    assert (names.contains("Task"));
    assert (names.contains("Agent"));
  }

  @Test
  void controllerShouldRenderInstancesView() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    IngestResult ingestResult = cortex.ingest(ttl);
    cortex.approve(ingestResult.branch());

    Model model = new ExtendedModelMap();
    String view = ingestController.getAssertions("Task", model);
    assert (view.equals("instances"));
    assert ("Task".equals(model.getAttribute("type")));

    @SuppressWarnings("unchecked")
    List<String> instances = (List<String>) model.getAttribute("instances");
    assert (instances != null);
    assert (instances.contains("assertions/ValidTask"));
  }

  @Test
  void controllerShouldRenderDescribeView() {
    String id = "test";

    Model model = new ExtendedModelMap();
    String view = ingestController.describe(id, model);
    assert (view.equals("describe"));
    assert (id.equals(model.getAttribute("subject")));
    assert (cortex.describe(id).equals(model.getAttribute("statements")));
  }

  @Test
  void describeShouldIncludeProvenance() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    IngestResult ingestResult = cortex.ingest(ttl);
    cortex.approve(ingestResult.branch());

    List<ProvenancedStatement> statements = cortex.describe("assertions/ValidTask");
    assert (!statements.isEmpty());
    ProvenancedStatement assigned =
        statements.stream()
            .filter(statement -> statement.predicate().contains("assignedTo"))
            .findFirst()
            .orElseThrow();
    assert (assigned.created() != null);
  }
}
