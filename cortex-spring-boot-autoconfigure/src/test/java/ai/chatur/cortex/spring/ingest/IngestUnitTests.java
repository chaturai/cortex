package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.spring.CortexConfiguration;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CortexConfiguration.class)
public class IngestUnitTests {

  @Autowired
  @Qualifier("assertions")
  Dataset assertions;

  @Autowired Cortex cortex;

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
  void shouldNotStageInvalidAssertions() throws IOException {
    String ttl = invalidAssertion.getContentAsString(Charset.defaultCharset());
    IngestResult ingestResult = cortex.ingest(ttl);
    assert (!ingestResult.valid());
    assert (ingestResult.branch() == null);
    assert (ingestResult.errors() != null);
  }
}
