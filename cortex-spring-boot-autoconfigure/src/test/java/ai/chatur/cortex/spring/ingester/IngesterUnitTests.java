package ai.chatur.cortex.spring.ingester;

import ai.chatur.cortex.IngestService;
import ai.chatur.cortex.spring.core.AssertionRepository;
import ai.chatur.cortex.spring.core.CoreConfiguration;
import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {IngesterConfiguration.class, CoreConfiguration.class})
public class IngesterUnitTests {

  @Autowired AssertionRepository assertionRepository;
  @Autowired IngestService ingestService;

  @Autowired
  @Value("assertions/valid.ttl")
  Resource validAssertion;

  @Test
  void shouldStageValidAssertions() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    String branch = ingestService.ingest(ttl);
    assert (assertionRepository.hasBranch(branch));
  }
}
