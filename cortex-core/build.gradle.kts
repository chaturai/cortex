plugins {
  id("cortex-library-conventions")
}

description =
  "Core Cortex implementation built on Apache Jena, providing ontology-backed knowledge graph storage, provenance tracking, inference, and full-text search"

dependencies {
  api(project(":cortex-api"))

  implementation(libs.slf4j.api)

  // Apache Jena
  api(platform(libs.jena.bom))
  api("org.apache.jena:jena-tdb2")
  api("org.apache.jena:jena-ontapi")
  api("org.apache.jena:jena-rdfpatch")
  api("org.apache.jena:jena-shacl")
  api("org.apache.jena:jena-text")

  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
