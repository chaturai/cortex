package ai.chatur.cortex.core.store;

import static org.assertj.core.api.Assertions.assertThat;

import ai.chatur.cortex.core.ontology.OntologyLoader;
import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AssertionStore}, isolated from the rest of {@link ai.chatur.cortex.Cortex}
 * — no {@code CortexBuilder}, no reasoner, no lint/ingest services.
 */
class AssertionStoreTests {

  @Test
  void openShouldUseTdb2EvenInMemory() {
    OntModel ontModel = OntologyLoader.load(List.of(CortexFixtures.ONTOLOGY));

    Dataset assertions = AssertionStore.open(false, null, ontModel);

    assertThat(TDB2Factory.isTDB2(assertions))
        .as("the in-memory assertions dataset is still backed by TDB2, not a plain in-memory model")
        .isTrue();
  }
}
