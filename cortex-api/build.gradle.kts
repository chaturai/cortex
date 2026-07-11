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
  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
