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
  implementation(project(":cortex-core"))

  implementation(platform(libs.spring.boot.dependencies))
  implementation("org.springframework.boot:spring-boot-autoconfigure")

  implementation(platform(libs.spring.ai.bom))
  implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
