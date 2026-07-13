plugins {
  id("cortex-library-conventions")
  `jacoco-report-aggregation`
}

description =
  "Spring Boot auto-configuration for Cortex, including an MCP server exposing the knowledge graph to AI agents"

dependencies {
  api(project(":cortex-core"))

  implementation(libs.slf4j.api)

  implementation(platform(libs.spring.boot.dependencies))
  implementation("org.springframework.boot:spring-boot-autoconfigure")

  implementation(platform(libs.spring.ai.bom))
  implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.ai:spring-ai-starter-mcp-client-webmvc")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
