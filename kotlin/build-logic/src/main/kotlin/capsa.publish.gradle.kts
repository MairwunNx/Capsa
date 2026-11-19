// Maven coordinates, the POM, and Maven Central for every published Capsa module.
// `./gradlew publishAndReleaseToMavenCentral` uploads every module to the Central Portal and releases it once Central validates it.
// The release workflow hands in the token and the key as Gradle properties, through the environment:
// ORG_GRADLE_PROJECT_mavenCentralUsername, ORG_GRADLE_PROJECT_mavenCentralPassword,
// ORG_GRADLE_PROJECT_signingInMemoryKey (the armored key) and ORG_GRADLE_PROJECT_signingInMemoryKeyPassword.

plugins {
    id("com.vanniktech.maven.publish")
}

group = "com.mairwunnx.capsa"
version = providers.gradleProperty("capsaVersion").getOrElse("0.0.0-dev")

mavenPublishing {
    coordinates(group.toString(), project.name, version.toString())

    publishToMavenCentral(automaticRelease = true)

    // Without a key the build signs nothing, so publishToMavenLocal works on any machine. Central rejects what isn't signed.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set(project.name)
        description.set(provider { project.description ?: project.name })
        url.set("https://github.com/MairwunNx/Capsa/wiki")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("mairwunnx")
                name.set("Pavel Erokhin")
                url.set("https://github.com/MairwunNx")
            }
        }
        scm {
            url.set("https://github.com/MairwunNx/Capsa")
            connection.set("scm:git:https://github.com/MairwunNx/Capsa.git")
            developerConnection.set("scm:git:ssh://git@github.com/MairwunNx/Capsa.git")
        }
    }
}
