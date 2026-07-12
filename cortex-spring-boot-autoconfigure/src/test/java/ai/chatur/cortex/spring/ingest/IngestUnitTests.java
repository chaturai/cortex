package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
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
  void controllerShouldRenderBranchView() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    IngestResult ingestResult = cortex.ingest(ttl);
    String branch = ingestResult.branch();

    Model model = new ExtendedModelMap();
    String view = ingestController.getBranch(branch, model);
    assert (view.equals("branch"));
    assert (branch.equals(model.getAttribute("branch")));
    assert (cortex.getBranch(branch).equals(model.getAttribute("graph")));
  }

  @Test
  void controllerShouldRenderOntologyView() {
    String id = "test";

    Model model = new ExtendedModelMap();
    String view = ingestController.describe(id, model);
    assert (view.equals("ontology"));
    assert (id.equals(model.getAttribute("id")));
    assert (cortex.describe(id).equals(model.getAttribute("description")));
  }
}
