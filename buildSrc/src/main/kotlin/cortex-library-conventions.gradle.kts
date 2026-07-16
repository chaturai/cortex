import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("cortex-common-conventions")
    `java-library`
    jacoco
    id("com.vanniktech.maven.publish")
}

// Full strict linting, including `missing`: every public member of cortex-api, cortex-core, and
// cortex-spring-boot-autoconfigure is documented as of Phase 9, so there is no tail of warnings to
// carve out. This used to be `-Xdoclint:none`, which silenced everything (including `missing`) and
// is why ~45 undocumented public members and several stale `@link`s shipped unnoticed.
tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all", true)
}

tasks.test { finalizedBy(tasks.jacocoTestReport) }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true }
}

// Coverage floors, measured from the real numbers after Phase 8b's aggregation fix and gap-closing
// tests: cortex-core's own (non-aggregated) jacocoTestReport measured LINE 93.56%/INSTRUCTION
// 92.78%; cortex-spring-boot-autoconfigure's measured LINE 92.76%/INSTRUCTION 93.30%. 85% is a
// floor with real headroom below both — enough to catch an actual regression (a class landing with
// no tests, a large untested branch) without being so tight that routine, honest changes trip it.
// Gated on LINE, the metric most build-breakage discussions center on; BRANCH coverage (75%/100%
// today) is intentionally not gated yet — cortex-core's is meaningfully lower and would need real
// investment to move rather than a threshold fight.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
    // cortex-api ships only records and interfaces with no test suite of its own — see the comment
    // in its build.gradle.kts. Its own, isolated coverage report is empty (not zero-with-data, but
    // no execution data at all, since its `test` task is NO-SOURCE), so gating it against a fixed
    // floor would fail the build for a module with nothing of its own to test. It IS exercised, and
    // covered, through cortex-core's tests — see the aggregated
    // `:cortex-spring-boot-autoconfigure:testCodeCoverageReport` — this only skips the verification
    // of its own standalone report.
    onlyIf { sourceSets["test"].allSource.files.isNotEmpty() }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))

    coordinates(project.group.toString(), project.name, project.version.toString())

    pom {
        name.set(project.name)
        description.set(provider { project.description ?: "Cortex ${project.name} module" })
        url.set("https://github.com/chaturai/cortex")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("ajatix")
                name.set("Ajay Viswanathan")
                email.set("ajay.viswanathan@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/chaturai/cortex.git")
            developerConnection.set("scm:git:ssh://git@github.com/chaturai/cortex.git")
            url.set("https://github.com/chaturai/cortex")
        }
    }
}
