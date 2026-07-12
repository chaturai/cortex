package ai.chatur.cortex;

import java.io.IOException;

public interface IngestService {

  IngestResult ingest(String ttl) throws IOException;

  boolean approve(String branch);

  boolean reject(String branch);
}
