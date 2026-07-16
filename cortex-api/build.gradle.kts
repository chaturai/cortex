plugins {
  id("cortex-library-conventions")
}

description =
  "Public API for Cortex, a knowledge graph memory store with branching ingest, provenance, inference, and SPARQL querying"

// No testImplementation/testRuntimeOnly JUnit dependencies here deliberately: this module is
// records and interfaces with no logic of its own — accessors, and interface method signatures
// with only Javadoc — so a test suite here would only assert what the compiler already guarantees
// (a record returns what its constructor was given). The types are exercised for real, with actual
// behavior around them, by cortex-core's tests (see CortexBuilder-based tests using Term,
// IngestResult, BranchChange, etc.), which is where their coverage numbers come from in the
// aggregated report.
