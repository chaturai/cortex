plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.spotless.plugin.gradle)
    implementation(libs.gradle.maven.publish.plugin)
}
