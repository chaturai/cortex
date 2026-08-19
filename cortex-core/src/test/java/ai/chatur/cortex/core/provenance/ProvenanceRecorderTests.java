package ai.chatur.cortex.core.provenance;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.core.CortexNamespace;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the provenance an approval clears when it retracts statements — the half of the
 * bookkeeping that is invisible through {@code describe}, since a statement that is gone reports
 * nothing either way.
 */
class ProvenanceRecorderTests {

  private final ProvenanceRecorder recorder = new ProvenanceRecorder();
  private final Resource activity = CortexNamespace.getResource("branch-1");

  @Test
  void shouldClearOnlyTheProvenanceOfTheRetractedStatement() {
    Model data = ModelFactory.createDefaultModel();
    Resource agent = data.createResource("example://kb/Agent1");
    Statement retracted = data.createStatement(agent, RDFS.label, "the agent");
    Statement kept = data.createStatement(agent, RDFS.comment, "still here");
    data.add(retracted).add(kept);
    Model provenance = recorder.getProvenance(data, data, activity);

    Model remaining =
        provenance.difference(recorder.getStaleProvenance(only(retracted), provenance));

    assertThat(recorder.getStaleProvenance(only(retracted), remaining).isEmpty())
        .as(
            "the retracted statement has no provenance left, so re-approving it later records a"
                + " fresh creation time rather than reporting the one it had before it went away")
        .isTrue();
    assertThat(recorder.getStaleProvenance(only(kept), remaining).isEmpty())
        .as("the statement that survives keeps its own reifier")
        .isFalse();
  }

  @Test
  void shouldClearNothingForAStatementThatNeverHadProvenance() {
    Model data = ModelFactory.createDefaultModel();
    data.add(data.createResource("example://kb/Agent1"), RDFS.label, "never approved");

    assertThat(recorder.getStaleProvenance(data, ModelFactory.createDefaultModel()).isEmpty())
        .isTrue();
  }

  private Model only(Statement statement) {
    return ModelFactory.createDefaultModel().add(statement);
  }
}
