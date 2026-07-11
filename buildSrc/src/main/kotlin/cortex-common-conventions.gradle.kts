plugins {
    java
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

tasks {
    java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
    spotless {
        java { googleJavaFormat() }
        kotlinGradle { ktfmt().googleStyle() }
    }
    test { useJUnitPlatform() }
}


