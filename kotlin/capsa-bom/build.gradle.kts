plugins {
    `java-platform`
    id("capsa.publish")
}

description = "Aligns the versions of every Capsa module."

dependencies {
    constraints {
        api(projects.capsaCore)
        api(projects.capsaHost)
        api(projects.capsaViewmodel)
        api(projects.capsaCompose)
        api(projects.capsaTest)
        api(projects.capsaViewmodelTest)
        api(projects.capsaDetekt)
    }
}
