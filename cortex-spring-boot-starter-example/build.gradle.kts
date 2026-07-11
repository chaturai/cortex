plugins {
  id("cortex-common-conventions")
  id("org.springframework.boot") version "4.0.7"
  id("io.spring.dependency-management") version "1.1.7"
}

tasks {
  java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
  test { useJUnitPlatform() }
}

dependencies {
  implementation(project(":cortex-spring-boot-starter"))
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-quartz")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
  implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
  testImplementation("org.springframework.boot:spring-boot-starter-quartz-test")
  testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.ai:spring-ai-bom:2.0.0")
  }
}
