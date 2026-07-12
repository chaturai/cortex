package ai.chatur.cortex.spring.ingester;

import ai.chatur.cortex.IngestService;
import ai.chatur.cortex.spring.dataset.DatasetConfiguration;
import ai.chatur.cortex.spring.dataset.DatasetService;
import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {IngesterConfiguration.class, DatasetConfiguration.class, DatasetService.class})
public class IngesterUnitTests {

  @Autowired DatasetService datasetService;
  @Autowired IngestService ingestService;

  @Autowired
  @Value("assertions/valid.ttl")
  Resource validAssertion;

  @Test
  void shouldStageValidAssertions() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    ingestService.ingest(ttl);
    datasetService.printAssertions();
  }
}
