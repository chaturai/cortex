plugins {
  kotlin("jvm") version "2.2.21"
  kotlin("plugin.spring") version "2.2.21"
  id("org.springframework.boot") version "4.0.7"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.diffplug.spotless") version "8.6.0"
}

repositories { mavenCentral() }

tasks {
  java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
  kotlin {
    compilerOptions {
      freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
  }
  spotless {
    kotlin { ktfmt() }
    kotlinGradle { ktfmt() }
  }
  test { useJUnitPlatform() }
}

dependencyManagement { imports { mavenBom("org.springframework.ai:spring-ai-bom:2.0.0") } }

dependencies {
  // Kotlin
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("tools.jackson.module:jackson-module-kotlin")

  // Spring Boot
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-quartz")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-webmvc")

  // Spring Boot External
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

  // Spring AI
  implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

  // Apache Jena
  implementation(platform("org.apache.jena:jena-bom:6.1.0"))
  implementation("org.apache.jena:jena-tdb2")
  implementation("org.apache.jena:jena-ontapi")
  implementation("org.apache.jena:jena-rdfpatch")
  implementation("org.apache.jena:jena-shacl")
  implementation("org.apache.jena:jena-text")

  // Testing
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
  testImplementation("org.springframework.boot:spring-boot-starter-cache-test")
  testImplementation("org.springframework.boot:spring-boot-starter-quartz-test")
  testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}
