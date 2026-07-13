package ai.chatur.cortex.spring.lint;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.LintResult;
import ai.chatur.cortex.spring.CortexConfiguration;
import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CortexConfiguration.class)
public class LintUnitTests {

  @Autowired Cortex cortex;

  @Autowired
  @Value("assertions/valid.ttl")
  Resource validAssertion;

  @Autowired
  @Value("assertions/invalid.ttl")
  Resource invalidAssertion;

  @Test
  void shouldReturnValidatedTtl() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    LintResult lintResult = cortex.lint(ttl);
    assert (lintResult.valid());
    assert (lintResult.ttl() != null);
    assert (lintResult.ttl().contains("assignedTo"));
    assert (lintResult.errors() == null);
  }

  @Test
  void validatedTtlShouldBeIngestible() throws IOException {
    String ttl = validAssertion.getContentAsString(Charset.defaultCharset());
    LintResult lintResult = cortex.lint(ttl);
    IngestResult ingestResult = cortex.ingest(lintResult.ttl());
    assert (ingestResult.valid());
  }

  @Test
  void shouldAllowTypeLabelAndCommentBeyondOntology() throws IOException {
    String ttl =
        """
        @prefix o: <cortex://ontology/> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix : <cortex://assertions/> .

        :LintTask a o:Task ;
            rdfs:label "Lint task" ;
            rdfs:comment "A task used to test linting" .
        """;
    LintResult lintResult = cortex.lint(ttl);
    assert (lintResult.valid());
    assert (lintResult.errors() == null);
  }

  @Test
  void shouldRejectPropertyNotInOntology() throws IOException {
    String ttl =
        """
        @prefix o: <cortex://ontology/> .
        @prefix : <cortex://assertions/> .

        :LintTask o:unknownProperty :LintAgent .
        """;
    LintResult lintResult = cortex.lint(ttl);
    assert (!lintResult.valid());
    assert (lintResult.ttl() == null);
    assert (lintResult.errors().contains("cortex://ontology/unknownProperty"));
  }

  @Test
  void shouldRejectClassNotInOntology() throws IOException {
    String ttl = invalidAssertion.getContentAsString(Charset.defaultCharset());
    LintResult lintResult = cortex.lint(ttl);
    assert (!lintResult.valid());
    assert (lintResult.ttl() == null);
    assert (lintResult.errors().contains("cortex://ontology/InvalidClass"));
  }

  @Test
  void shouldRejectMalformedTurtle() throws IOException {
    LintResult lintResult = cortex.lint("this is not turtle");
    assert (!lintResult.valid());
    assert (lintResult.ttl() == null);
    assert (lintResult.errors() != null);
  }
}
