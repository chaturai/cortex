package ai.chatur.cortex.spring.support;

import ai.chatur.cortex.CortexIngestor;
import ai.chatur.cortex.IngestResult;
import ai.chatur.cortex.ResourceEdit;

/**
 * Hand-rolled fake of {@link CortexIngestor} recording what it was handed, shared by the tests of
 * every controller and tool that stages something for review.
 *
 * <p>Shared rather than re-declared per test class because {@code CortexIngestor} carries two
 * methods and so is no longer a functional interface a test can supply as a lambda.
 */
public final class FakeIngestor implements CortexIngestor {

  private final IngestResult result;
  private String ingested;
  private ResourceEdit revised;

  /**
   * Creates the fake.
   *
   * @param result the outcome to answer every call with
   */
  public FakeIngestor(IngestResult result) {
    this.result = result;
  }

  @Override
  public IngestResult ingest(String ttl) {
    this.ingested = ttl;
    return result;
  }

  @Override
  public IngestResult revise(ResourceEdit edit) {
    this.revised = edit;
    return result;
  }

  /**
   * Returns the Turtle the fake was last asked to ingest.
   *
   * @return the ingested document, or {@code null} if nothing was ingested
   */
  public String ingested() {
    return ingested;
  }

  /**
   * Returns the edit the fake was last asked to stage.
   *
   * @return the proposed edit, or {@code null} if nothing was revised
   */
  public ResourceEdit revised() {
    return revised;
  }
}
