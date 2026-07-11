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

dependencies { api(project(":cortex-spring-boot-autoconfigure")) }
