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

  // Quartz and the AWS SDK back `CortexBackupAutoConfiguration` only, which is off by default
  // (`cortex.backup.enabled`/`cortex.s3.enabled`). Declaring them `compileOnly` keeps them out of
  // the
  // published POM, so the AWS SDK is not forced on every consumer of the starter — the beans that
  // need them are gated behind `@ConditionalOnClass`, and opting in means adding the dependency as
  // well as setting the property. This is deliberately the inverse of the webmvc/thymeleaf case in
  // cortex-spring-boot-starter, which the starter *must* carry because the web UI is on by default.
  // `software.amazon.awssdk:s3` declares apache-client and url-connection-client at *test* scope
  // and
  // ships only the async netty client at runtime, so the synchronous `S3Client` has no HTTP
  // implementation unless one is added explicitly — without this it fails at build() with "Unable
  // to
  // load an HTTP implementation from any provider in the chain". apache-client is also what backs
  // `cortex.s3.proxy`.
  compileOnly(platform(libs.spring.boot.dependencies))
  compileOnly("org.springframework.boot:spring-boot-starter-quartz")
  compileOnly(platform(libs.aws.sdk.bom))
  compileOnly("software.amazon.awssdk:s3")
  compileOnly("software.amazon.awssdk:apache-client")

  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
  testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  testImplementation("org.springframework.boot:spring-boot-starter-quartz")
  testImplementation("org.springframework.ai:spring-ai-starter-mcp-client")
  testImplementation(platform(libs.aws.sdk.bom))
  testImplementation("software.amazon.awssdk:s3")
  testImplementation("software.amazon.awssdk:apache-client")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
