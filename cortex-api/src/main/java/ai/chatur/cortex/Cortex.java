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

  List<OntologyClass> getClassHierarchy();

  List<String> getInstances(String type);

  List<ProvenancedStatement> describe(String id);

  String query(String sparql) throws IOException;

  String search(String text);

  void recomputeInference();
}
