plugins {
  id("cortex-library-conventions")
  `jacoco-report-aggregation`
}

description =
  "Spring Boot auto-configuration for Cortex, including an MCP server exposing the knowledge graph to AI agents"

dependencies {
  api(project(":cortex-core"))

  // Redundant with the `api(project(":cortex-core"))` dependency above for coverage-aggregation
  // purposes today — Gradle's variant-aware resolution already surfaces cortex-core's (and,
  // transitively, cortex-api's) JaCoCo coverage-data variant through that normal project
  // dependency edge, since both apply the `jacoco` plugin via `cortex-library-conventions`.
  // Declared explicitly anyway so the aggregation doesn't silently go dark if that dependency is
  // ever changed to `implementation`-only in a different configuration, or resolved differently by
  // a future Gradle version.
  jacocoAggregation(project(":cortex-core"))
  jacocoAggregation(project(":cortex-api"))

  implementation(libs.slf4j.api)

  implementation(platform(libs.spring.boot.dependencies))
  implementation("org.springframework.boot:spring-boot-autoconfigure")

  annotationProcessor(platform(libs.spring.boot.dependencies))
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

  implementation(platform(libs.spring.ai.bom))
  implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
  testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  testImplementation("org.springframework.ai:spring-ai-starter-mcp-client")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
