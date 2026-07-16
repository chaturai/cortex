plugins {
    java
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

spotless {
    java { googleJavaFormat() }
    kotlinGradle { ktfmt().googleStyle() }
}

tasks {
    // keep reflective parameter names available, e.g. for MCP tool schemas
    withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }
    test { useJUnitPlatform() }
}

