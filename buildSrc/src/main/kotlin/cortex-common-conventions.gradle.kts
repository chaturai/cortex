plugins {
    java
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

tasks {
    java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
    // keep reflective parameter names available, e.g. for MCP tool schemas
    withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }
    spotless {
        java { googleJavaFormat() }
        kotlinGradle { ktfmt().googleStyle() }
    }
    test { useJUnitPlatform() }
}


