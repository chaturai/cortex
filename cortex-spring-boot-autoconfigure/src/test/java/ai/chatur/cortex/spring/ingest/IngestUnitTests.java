package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchInfo;
import ai.chatur.cortex.BranchStatement;
import ai.chatur.cortex.BranchSubject;
import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.OntologyClass;
import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.spring.CortexConfiguration;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;
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

  /** Assertions never seen before, so every test stages a branch regardless of run order. */
  String freshAssertion() {
    return """
        @prefix o: <cortex://ontology/> .
        @prefix : <cortex://assertions/> .

        :Task-%1$s o:assignedTo :Agent-%1$s .
        """
        .formatted(UUID.randomUUID());
  }

  /** Approves the given assertions, tolerating that another test already approved them. */
  void approve(String ttl) throws IOException {
    IngestResult ingestResult = cortex.ingest(ttl);
    assert (ingestResult.valid());
    if (ingestResult.branch() != null) {
      cortex.approve(ingestResult.branch());
    }
  }

  @Test
  void assertionsShouldUseTDB2() {
    assert (TDB2Factory.isTDB2(assertions));
  }

  @Test
  void shouldStageValidAssertions() throws IOException {
    IngestResult ingestResult = cortex.ingest(freshAssertion());
    assert (ingestResult.valid());
    assert (cortex.hasBranch(ingestResult.branch()));
    assert (ingestResult.errors() == null);
  }

  @Test
  void controllerShouldRenderBranchesView() throws IOException {
    IngestResult ingestResult = cortex.ingest(freshAssertion());

    Model model = new ExtendedModelMap();
    String view = ingestController.listBranches(model);
    assert (view.equals("branches"));

    @SuppressWarnings("unchecked")
    List<BranchInfo> branches = (List<BranchInfo>) model.getAttribute("branches");
    assert (branches != null);
    BranchInfo branchInfo =
        branches.stream()
            .filter(info -> info.name().equals(ingestResult.branch()))
            .findFirst()
            .orElseThrow();
    assert (branchInfo.started() != null);
    assert (branchInfo.size() == 1);
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
    IngestResult ingestResult = cortex.ingest(freshAssertion());
    String branch = ingestResult.branch();

    Model model = new ExtendedModelMap();
    String view = ingestController.getBranch(branch, model);
    assert (view.equals("branch"));
    assert (branch.equals(model.getAttribute("branch")));

    @SuppressWarnings("unchecked")
    List<BranchSubject> subjects = (List<BranchSubject>) model.getAttribute("subjects");
    assert (subjects != null);
    assert (subjects.size() == 1);
    assert (subjects.getFirst().statements().size() == 1);
  }

  @Test
  void shouldUpdateBranchWithDeletionsAndEdits() throws IOException {
    String ttl =
        """
        @prefix o: <cortex://ontology/> .
        @prefix : <cortex://assertions/> .

        :UpdateTask o:assignedTo :FirstAgent ;
            o:assignedTo :DroppedAgent .
        """;
    IngestResult ingestResult = cortex.ingest(ttl);
    String branch = ingestResult.branch();

    List<BranchSubject> subjects = cortex.getBranchSubjects(branch);
    assert (subjects.size() == 1);
    BranchSubject subject = subjects.getFirst();
    BranchStatement dropped =
        subject.statements().stream()
            .filter(statement -> statement.object().contains("DroppedAgent"))
            .findFirst()
            .orElseThrow();
    BranchStatement edited =
        subject.statements().stream()
            .filter(statement -> statement.object().contains("FirstAgent"))
            .findFirst()
            .orElseThrow();

    boolean updated =
        cortex.updateBranch(
            branch,
            List.of(
                new BranchChange(
                    subject.uri(),
                    dropped.predicateUri(),
                    dropped.object(),
                    dropped.literal(),
                    dropped.datatype(),
                    null),
                new BranchChange(
                    subject.uri(),
                    edited.predicateUri(),
                    edited.object(),
                    edited.literal(),
                    edited.datatype(),
                    "cortex://assertions/EditedAgent")));
    assert (updated);

    List<BranchStatement> statements = cortex.getBranchSubjects(branch).getFirst().statements();
    assert (statements.size() == 1);
    assert (statements.getFirst().object().equals("cortex://assertions/EditedAgent"));
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
    approve(validAssertion.getContentAsString(Charset.defaultCharset()));

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
    approve(validAssertion.getContentAsString(Charset.defaultCharset()));

    List<ProvenancedStatement> statements = cortex.describe("assertions/ValidTask");
    assert (!statements.isEmpty());
    ProvenancedStatement assigned =
        statements.stream()
            .filter(statement -> statement.predicate().contains("assignedTo"))
            .findFirst()
            .orElseThrow();
    assert (assigned.created() != null);
  }

  @Test
  void shouldNotStageApprovedTriplesAgain() throws IOException {
    String ttl = freshAssertion();
    approve(ttl);

    IngestResult ingestResult = cortex.ingest(ttl);
    assert (ingestResult.valid());
    assert (ingestResult.branch() == null);
    assert (ingestResult.errors() == null);
  }

  @Test
  void shouldStageOnlyNovelTriples() throws IOException {
    String ttl =
        """
        @prefix o: <cortex://ontology/> .
        @prefix : <cortex://assertions/> .

        :PartialTask o:assignedTo :PartialAgent .
        """;
    approve(ttl);

    IngestResult ingestResult = cortex.ingest(ttl + "\n:PartialTask o:assignedTo :SecondAgent .");
    assert (ingestResult.valid());
    assert (cortex.hasBranch(ingestResult.branch()));

    String staged = cortex.getBranch(ingestResult.branch());
    assert (staged.contains("SecondAgent"));
    assert (!staged.contains("PartialAgent"));
  }

  @Test
  void shouldValidateAgainstApprovedAssertions() throws IOException {
    String ttl =
        """
        @prefix o: <cortex://ontology/> .
        @prefix : <cortex://assertions/> .

        :UnionAgent a o:Agent .

        :UnionTask a o:Task ;
            o:assignedTo :UnionAgent .
        """;
    approve(ttl);

    String update =
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix o: <cortex://ontology/> .
        @prefix : <cortex://assertions/> .

        :UnionTask a o:Task ;
            rdfs:label "union task" .
        """;
    IngestResult ingestResult = cortex.ingest(update);
    assert (ingestResult.valid());
    assert (cortex.hasBranch(ingestResult.branch()));

    String staged = cortex.getBranch(ingestResult.branch());
    assert (staged.contains("union task"));
    assert (!staged.contains("assignedTo"));
  }
}
