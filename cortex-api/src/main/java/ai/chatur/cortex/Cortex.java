package ai.chatur.cortex;

import java.io.IOException;
import java.io.OutputStream;

public interface Cortex {

  String getOntology() throws IOException;

  IngestResult ingest(String ttl) throws IOException;

  boolean hasBranch(String uri);

  boolean approve(String branch);

  boolean reject(String branch);

  void writeAssertions(OutputStream os);
}
