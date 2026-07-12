plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.8.0")
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.33.0")
}
