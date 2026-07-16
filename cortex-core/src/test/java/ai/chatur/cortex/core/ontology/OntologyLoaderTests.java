package ai.chatur.cortex.core.ontology;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.chatur.cortex.support.CortexFixtures;
import java.util.List;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.shared.PrefixMapping;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OntologyLoader}, isolated from the rest of {@link ai.chatur.cortex.Cortex}
 * — no {@code CortexBuilder}, no dataset, no reasoner.
 */
class OntologyLoaderTests {

  @Test
  void loadedModelPrefixMappingShouldBeImmutable() {
    OntModel ontModel = OntologyLoader.load(List.of(CortexFixtures.ONTOLOGY));

    assertThatThrownBy(() -> ontModel.setNsPrefix("", "cortex://ontology"))
        .as("the loader locks the model, so its prefix mapping can no longer be mutated")
        .isInstanceOf(PrefixMapping.JenaLockedException.class);
  }
}
