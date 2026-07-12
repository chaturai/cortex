package ai.chatur.cortex;

import java.io.IOException;

public interface IngestService {

  IngestResult ingest(String ttl) throws IOException;
}
