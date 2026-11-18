plugins {
    `java-platform`
    id("capsa.publish")
}

description = "Aligns the versions of every Capsa module."

dependencies {
    constraints {
        api(projects.capsaCore)
        api(projects.capsaViewmodel)
        api(projects.capsaCompose)
        api(projects.capsaTest)
    }
}
