package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.BranchChange;
import ai.chatur.cortex.BranchInfo;
import ai.chatur.cortex.BranchRename;
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
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task-%1$s :assignedTo kb:Agent-%1$s .
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
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:LintTask :unknownProperty kb:LintAgent .
        """;
    IngestResult ingestResult = cortex.ingest(ttl);
    assert (!ingestResult.valid());
    assert (ingestResult.branch() == null);
    assert (ingestResult.errors().contains("unknownProperty"));
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
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:UpdateTask :assignedTo kb:FirstAgent ;
            :assignedTo kb:DroppedAgent .
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
                    "example://kb/EditedAgent")));
    assert (updated);

    List<BranchStatement> statements = cortex.getBranchSubjects(branch).getFirst().statements();
    assert (statements.size() == 1);
    assert (statements.getFirst().object().equals("example://kb/EditedAgent"));
  }

  @Test
  void shouldRenameBranchSubjects() throws IOException {
    IngestResult ingestResult = cortex.ingest(freshAssertion());
    String branch = ingestResult.branch();

    BranchSubject task = cortex.getBranchSubjects(branch).getFirst();
    String agent = task.statements().getFirst().object();

    // Renaming the agent rewrites the statement referencing it as object
    boolean renamed =
        cortex.renameBranchSubjects(
            branch, List.of(new BranchRename(agent, "example://kb/RenamedAgent")));
    assert (renamed);
    List<BranchSubject> subjects = cortex.getBranchSubjects(branch);
    assert (subjects.size() == 1);
    assert (subjects.getFirst().uri().equals(task.uri()));
    assert (subjects
        .getFirst()
        .statements()
        .getFirst()
        .object()
        .equals("example://kb/RenamedAgent"));

    // Renaming the task removes the statements describing it
    renamed =
        cortex.renameBranchSubjects(
            branch, List.of(new BranchRename(task.uri(), "example://kb/RenamedTask")));
    assert (renamed);
    assert (cortex.getBranchSubjects(branch).isEmpty());

    assert (!cortex.renameBranchSubjects("unknown-branch", List.of()));
  }

  @Test
  void shouldRemoveReferencesToRemovedSubjects() throws IOException {
    String uuid = UUID.randomUUID().toString();
    String ttl =
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task-%1$s :assignedTo kb:Agent-%1$s .
        kb:Agent-%1$s rdfs:label "agent %1$s" .
        """
            .formatted(uuid);
    IngestResult ingestResult = cortex.ingest(ttl);
    String branch = ingestResult.branch();

    BranchSubject agent =
        cortex.getBranchSubjects(branch).stream()
            .filter(subject -> subject.name().startsWith("Agent"))
            .findFirst()
            .orElseThrow();
    BranchStatement label = agent.statements().getFirst();
    boolean updated =
        cortex.updateBranch(
            branch,
            List.of(
                new BranchChange(
                    agent.uri(),
                    label.predicateUri(),
                    label.object(),
                    label.literal(),
                    label.datatype(),
                    null)));
    assert (updated);

    // Removing the agent's last statement also removes the statement referencing it as object
    assert (cortex.getBranchSubjects(branch).isEmpty());
  }

  @Test
  void shouldRestoreAssertionsFromExport() throws IOException {
    String approved = freshAssertion();
    approve(approved);
    IngestResult staged = cortex.ingest(freshAssertion());

    String backup = cortex.exportAssertions();
    assert (backup.contains("assignedTo"));

    cortex.reject(staged.branch());
    cortex.importAssertions(backup);

    // Both the approved assertions and the staged branch are restored
    assert (cortex.getAssertions().contains("assignedTo"));
    assert (cortex.hasBranch(staged.branch()));
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
    String view = ingestController.getAssertions("example://ontology#Task", model);
    assert (view.equals("instances"));
    assert ("example://ontology#Task".equals(model.getAttribute("type")));

    @SuppressWarnings("unchecked")
    List<String> instances = (List<String>) model.getAttribute("instances");
    assert (instances != null);
    assert (instances.contains("example://kb/ValidTask"));
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

    List<ProvenancedStatement> statements = cortex.describe("example://kb/ValidTask");
    assert (!statements.isEmpty());
    ProvenancedStatement assigned =
        statements.stream()
            .filter(
                statement -> "example://ontology#assignedTo".equals(statement.predicate().uri()))
            .findFirst()
            .orElseThrow();
    assert (assigned.created() != null);
    assert ("kb".equals(assigned.object().prefix()));
    assert ("ValidAgent".equals(assigned.object().localName()));
    assert ("example://kb/ValidAgent".equals(assigned.object().uri()));
  }

  @Test
  void describeShouldReturnLiteralsWithoutUri() throws IOException {
    String uuid = UUID.randomUUID().toString();
    approve(
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task-%1$s :assignedTo kb:Agent-%1$s .
        kb:Agent-%1$s rdfs:label "agent %1$s" .
        """
            .formatted(uuid));

    List<ProvenancedStatement> statements = cortex.describe("example://kb/Agent-" + uuid);
    ProvenancedStatement label =
        statements.stream()
            .filter(
                statement ->
                    "http://www.w3.org/2000/01/rdf-schema#label"
                        .equals(statement.predicate().uri()))
            .findFirst()
            .orElseThrow();
    assert ("rdfs".equals(label.predicate().prefix()));
    assert ("label".equals(label.predicate().localName()));
    assert (label.object().prefix() == null);
    assert (label.object().uri() == null);
    assert (("agent " + uuid).equals(label.object().localName()));
  }

  @Test
  void controllerShouldRenderDescribeViewForFullUri() throws IOException {
    approve(validAssertion.getContentAsString(Charset.defaultCharset()));

    Model model = new ExtendedModelMap();
    String view = ingestController.describeUri("example://kb/ValidTask", model);
    assert (view.equals("describe"));
    assert ("example://kb/ValidTask".equals(model.getAttribute("subject")));

    @SuppressWarnings("unchecked")
    List<ProvenancedStatement> statements =
        (List<ProvenancedStatement>) model.getAttribute("statements");
    assert (statements != null);
    assert (!statements.isEmpty());
  }

  @Test
  void shouldNotDuplicateTriplesStagedOnConcurrentBranches() throws IOException {
    String uuid = UUID.randomUUID().toString();
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task-%1$s :assignedTo kb:Agent-%1$s .
        """
            .formatted(uuid);
    IngestResult first = cortex.ingest(ttl);
    IngestResult second = cortex.ingest(ttl);
    assert (cortex.hasBranch(first.branch()));
    assert (cortex.hasBranch(second.branch()));

    cortex.approve(first.branch());
    cortex.approve(second.branch());

    List<ProvenancedStatement> statements = cortex.describe("example://kb/Task-" + uuid);
    assert (statements.size() == statements.stream().distinct().count());
    assert (statements.stream()
            .filter(
                statement -> "example://ontology#assignedTo".equals(statement.predicate().uri()))
            .count()
        == 1);
  }

  @Test
  void listBranchesShouldExcludeProvenanceGraph() throws IOException {
    approve(freshAssertion());

    assert (!cortex.listBranches().contains("provenance"));
    assert (!cortex.hasBranch("provenance"));
  }

  @Test
  void assertionsShouldNotCarryProvenance() throws IOException {
    approve(freshAssertion());

    String trig = cortex.getAssertions();
    assert (trig.contains("assignedTo"));
    assert (!trig.contains("reifies"));
    assert (!trig.contains("wasGeneratedBy"));
    assert (!trig.contains("endedAtTime"));
  }

  @Test
  void approvalsShouldExtendInferenceIncrementally() throws IOException {
    String firstUuid = UUID.randomUUID().toString();
    String secondUuid = UUID.randomUUID().toString();
    String template =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:Task-%1$s :assignedTo kb:Agent-%1$s .
        """;
    approve(template.formatted(firstUuid));
    approve(template.formatted(secondUuid));

    // The tasks are typed by the domain rule, without any explicit recomputation
    List<String> instances = cortex.getInstances("example://ontology#Task");
    assert (instances.contains("example://kb/Task-" + firstUuid));
    assert (instances.contains("example://kb/Task-" + secondUuid));
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
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:PartialTask :assignedTo kb:PartialAgent .
        """;
    approve(ttl);

    IngestResult ingestResult =
        cortex.ingest(ttl + "\nkb:PartialTask :assignedTo kb:SecondAgent .");
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
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:UnionAgent a :Agent .

        kb:UnionTask a :Task ;
            :assignedTo kb:UnionAgent .
        """;
    approve(ttl);

    String update =
        """
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:UnionTask a :Task ;
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
