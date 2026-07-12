plugins {
  id("cortex-library-conventions")
}

description =
  "Public API for Cortex, a knowledge graph memory store with branching ingest, provenance, inference, and SPARQL querying"

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
