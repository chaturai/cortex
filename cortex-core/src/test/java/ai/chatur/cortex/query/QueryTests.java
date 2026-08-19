package ai.chatur.cortex.query;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.Cortex;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.ProvenancedStatement;
import ai.chatur.cortex.StatementOrigin;
import ai.chatur.cortex.Term;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Core behavior tests for {@link Cortex}'s query role: {@code describe}, {@code getInstances}, and
 * the shared {@code Terms.of} encoding they both go through.
 *
 * <p>Each test gets its own fresh, fully in-memory graph (see {@link CortexFixtures#newCortex()}).
 */
class QueryTests {

  private Cortex cortex;

  @BeforeEach
  void setUp() {
    cortex = CortexFixtures.newCortex();
  }

  @Test
  void describeShouldIncludeProvenance() {
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:DescribeTask :assignedTo kb:DescribeAgent .
                """)
            .branch());

    List<ProvenancedStatement> statements = cortex.describe("example://kb/DescribeTask");

    assertThat(statements).isNotEmpty();
    ProvenancedStatement assigned =
        statements.stream()
            .filter(
                statement -> "example://ontology#assignedTo".equals(statement.predicate().uri()))
            .findFirst()
            .orElseThrow();
    assertThat(assigned.created())
        .as("the approved statement's provenance timestamp is recorded")
        .isNotNull();
    assertThat(assigned.object())
        .as("a prefixed IRI is encoded as Term(prefix, localName, uri)")
        .isEqualTo(new Term("kb", "DescribeAgent", "example://kb/DescribeAgent"));
  }

  @Test
  void describeShouldReturnLiteralsWithoutUri() {
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:LabeledTask :assignedTo kb:LabeledAgent .
                kb:LabeledAgent rdfs:label "the labeled agent" .
                """)
            .branch());

    List<ProvenancedStatement> statements = cortex.describe("example://kb/LabeledAgent");
    ProvenancedStatement label =
        statements.stream()
            .filter(
                statement ->
                    "http://www.w3.org/2000/01/rdf-schema#label"
                        .equals(statement.predicate().uri()))
            .findFirst()
            .orElseThrow();

    assertThat(label.predicate().prefix()).isEqualTo("rdfs");
    assertThat(label.predicate().localName()).isEqualTo("label");
    assertThat(label.object().prefix()).as("a literal has no prefix").isNull();
    assertThat(label.object().uri()).as("a literal has no uri").isNull();
    assertThat(label.object().localName())
        .as("a literal's localName is its lexical form")
        .isEqualTo("the labeled agent");
  }

  @Test
  void countInstancesShouldCountInstancesOfEachClassIncludingInferredTypes() {
    // the two tasks are typed only by inference — the domain of :assignedTo is :Task, its range
    // :Agent — so this also pins that countInstances counts over the inference closure
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:CountTaskA :assignedTo kb:CountAgentA .
                kb:CountTaskB :assignedTo kb:CountAgentA .
                """)
            .branch());

    Map<String, Long> counts = cortex.countInstances();

    assertThat(counts)
        .as("countInstances counts the instances of each class, inferred types included")
        .containsEntry("example://ontology#Task", 2L)
        .containsEntry("example://ontology#Agent", 1L);
    assertThat(counts)
        .as("a class with no instances is absent rather than mapped to zero")
        .doesNotContainKey("example://ontology#NonexistentClass");
  }

  @Test
  void getInstancesShouldEncodePrefixedIriAsPrefixAndLocalName() {
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:EncodedTask :assignedTo kb:EncodedAgent .
                """)
            .branch());

    List<Term> instances = cortex.getInstances("example://ontology#Task");

    assertThat(instances)
        .as("getInstances encodes a prefixed IRI as Term(prefix, localName, uri)")
        .contains(new Term("kb", "EncodedTask", "example://kb/EncodedTask"));
  }

  @Test
  void describeAndGetInstancesShouldAgreeOnUnprefixedIriEncoding() {
    // RESOLVED (Phase 2b): the same unprefixed node used to be encoded two different ways by two
    // different call paths (QueryService.listInstances via getNsURIPrefix+getLocalName, versus
    // QueryService.describe via ontModel.shortForm split on ':'). Both now go through Terms.of, so
    // they agree: prefix is null and localName is the full URI, per Term.java's own javadoc ("the
    // full URI if no prefix matches").
    String agentUri = "http://example.org/unprefixed/UnprefixedAgent";
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .
                @prefix ext: <http://example.org/unprefixed/> .

                ext:UnprefixedAgent a :Agent .

                kb:UnprefixedTask a :Task ;
                    :assignedTo ext:UnprefixedAgent .
                """)
            .branch());

    List<Term> instances = cortex.getInstances("example://ontology#Agent");
    Term viaGetInstances =
        instances.stream().filter(term -> agentUri.equals(term.uri())).findFirst().orElseThrow();
    assertThat(viaGetInstances)
        .as("getInstances on an unprefixed IRI: prefix is null and localName is the full URI")
        .isEqualTo(new Term(null, agentUri, agentUri));

    List<ProvenancedStatement> statements = cortex.describe("example://kb/UnprefixedTask");
    ProvenancedStatement assignedTo =
        statements.stream()
            .filter(
                statement -> "example://ontology#assignedTo".equals(statement.predicate().uri()))
            .findFirst()
            .orElseThrow();
    assertThat(assignedTo.object())
        .as(
            "describe on the SAME unprefixed IRI agrees with getInstances above — both now share"
                + " the same Terms.of construction, which is the contradiction Phase 2b fixed")
        .isEqualTo(viaGetInstances);
  }

  @Test
  void describeShouldDistinguishAssertedOntologyAndInferredStatements() {
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:OriginTask :assignedTo kb:OriginAgent .
                """)
            .branch());

    List<ProvenancedStatement> statements = cortex.describe("example://kb/OriginTask");

    assertThat(originOf(statements, "example://ontology#assignedTo"))
        .as("an approved statement carries provenance")
        .isEqualTo(StatementOrigin.ASSERTED);
    assertThat(originOf(statements, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))
        .as("rdf:type :Task was derived by the domain rule, not asserted")
        .isEqualTo(StatementOrigin.INFERRED);
  }

  @Test
  void describeShouldMarkOntologyAxiomsAsOntologyRatherThanInferred() {
    // the closure is only materialized once something has been approved
    cortex.approve(
        cortex
            .ingest(
                """
                @prefix : <example://ontology#> .
                @prefix kb: <example://kb/> .

                kb:AxiomTask :assignedTo kb:AxiomAgent .
                """)
            .branch());

    List<ProvenancedStatement> statements = cortex.describe("example://ontology#assignedTo");

    assertThat(statements)
        .as("the reasoner is bound to the ontology, so its axioms are visible through describe")
        .isNotEmpty();
    assertThat(originOf(statements, "http://www.w3.org/2000/01/rdf-schema#domain"))
        .as("rdfs:domain comes verbatim from the ontology; it is not a reasoner conclusion")
        .isEqualTo(StatementOrigin.ONTOLOGY);
    assertThat(originOf(statements, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))
        .as("the property's own declaration comes from the ontology too")
        .isEqualTo(StatementOrigin.ONTOLOGY);
    assertThat(statements)
        .as("no ontology axiom is reported as approved")
        .allMatch(statement -> statement.created() == null);
  }

  private StatementOrigin originOf(List<ProvenancedStatement> statements, String predicateUri) {
    return statements.stream()
        .filter(statement -> predicateUri.equals(statement.predicate().uri()))
        .findFirst()
        .orElseThrow()
        .origin();
  }

  @Test
  void shouldNotDuplicateTriplesStagedOnConcurrentBranches() {
    String ttl =
        """
        @prefix : <example://ontology#> .
        @prefix kb: <example://kb/> .

        kb:ConcurrentTask :assignedTo kb:ConcurrentAgent .
        """;
    IngestResult first = cortex.ingest(ttl);
    IngestResult second = cortex.ingest(ttl);
    assertThat(cortex.hasBranch(first.branch())).isTrue();
    assertThat(cortex.hasBranch(second.branch())).isTrue();

    cortex.approve(first.branch());
    cortex.approve(second.branch());

    List<ProvenancedStatement> statements = cortex.describe("example://kb/ConcurrentTask");
    assertThat(statements)
        .as("approving two branches staging the same triple does not duplicate it")
        .hasSize((int) statements.stream().distinct().count());
    assertThat(
            statements.stream()
                .filter(
                    statement ->
                        "example://ontology#assignedTo".equals(statement.predicate().uri()))
                .count())
        .isEqualTo(1);
  }
}
