import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("cortex-common-conventions")
    `java-library`
    id("com.vanniktech.maven.publish")
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

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
                id.set("ajayviswanathan")
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
