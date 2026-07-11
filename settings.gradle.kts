rootProject.name = "cortex"

pluginManagement { repositories { gradlePluginPortal() } }

dependencyResolutionManagement { @Suppress("UnstableApiUsage") repositories { mavenCentral() } }

include("cortex-api")

include("cortex-core")

include("cortex-spring-boot-autoconfigure")

include("cortex-spring-boot-starter")
