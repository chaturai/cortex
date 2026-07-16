plugins {
  id("cortex-common-conventions")
  alias(libs.plugins.spring.boot)
}

dependencies {
  implementation(platform(libs.spring.boot.dependencies))
  implementation(platform(libs.spring.ai.bom))
  annotationProcessor(platform(libs.spring.boot.dependencies))

  // Web UI (webmvc, thymeleaf) and the MCP server are now pulled in transitively by
  // cortex-spring-boot-starter (Phase 7) — see cortex-spring-boot-starter/build.gradle.kts.
  implementation(project(":cortex-spring-boot-starter"))
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  developmentOnly(platform(libs.spring.boot.dependencies))
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  testImplementation(platform(libs.spring.boot.dependencies))
  testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
  testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
