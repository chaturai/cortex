package ai.chatur.cortex;

import java.io.IOException;
import java.util.List;

public interface Cortex {

  String getOntology() throws IOException;

  IngestResult ingest(String ttl) throws IOException;

  List<String> listBranches();

  boolean hasBranch(String branch);

  String getBranch(String branch) throws IOException;

  void approve(String branch);

  void reject(String branch);

  String getAssertions() throws IOException;

  String describe(String id) throws IOException;

  String query(String sparql) throws IOException;
}
