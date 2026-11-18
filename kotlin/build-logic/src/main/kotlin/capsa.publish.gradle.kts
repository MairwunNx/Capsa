// Maven coordinates and the POM of every published Capsa module. Signing and Maven Central come later.

plugins {
    id("com.vanniktech.maven.publish")
}

group = "com.mairwunnx.capsa"
version = providers.gradleProperty("capsaVersion").getOrElse("0.0.0-dev")

mavenPublishing {
    coordinates(group.toString(), project.name, version.toString())

    pom {
        name.set(project.name)
        description.set(provider { project.description ?: project.name })
        url.set("https://github.com/MairwunNx/Capsa")
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
