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
  testImplementation(platform("org.junit:junit-bom:6.0.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
