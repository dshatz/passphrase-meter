plugins {
    id("com.vanniktech.maven.publish")
    signing
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = "com.dshatz.passphrasemeter",
        artifactId = "passphrase-meter",
        version = version.toString(),
    )

    pom {
        name.set("passphrase-meter")
        description.set("Kotlin Multiplatform password and PIN strength meter based on zxcvbn.")
        url.set("https://github.com/dshatz/passphrase-meter")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            url.set("https://github.com/dshatz/passphrase-meter")
            connection.set("scm:git:git://github.com/dshatz/passphrase-meter")
            developerConnection.set("scm:git:git://github.com/dshatz/passphrase-meter.git")
        }

        developers {
            developer {
                id.set("dshatz")
                name.set("Daniels Šatcs")
                url.set("https://github.com/dshatz")
            }
        }
    }
}

signing {
    // Local publishing (publishToMavenLocal) works without keys.
    setRequired {
        !gradle.taskGraph.allTasks.any { it is PublishToMavenLocal }
    }
}
