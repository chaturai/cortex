plugins {
  id("java-library")
  id("com.diffplug.spotless")
}

tasks {
  java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
  spotless {
    java { googleJavaFormat() }
    kotlinGradle { ktfmt().googleStyle() }
  }
  test { useJUnitPlatform() }
}

dependencies {
  api(project(":cortex-api"))

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
