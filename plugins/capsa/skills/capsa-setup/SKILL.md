---
name: capsa-setup
description: Add Capsa to a project – the dependencies, the agent skills, and one line in the project’s instructions file for agents. Use when the user asks to add, install, or set up Capsa or Capability Slice Architecture in a project.
argument-hint: "[modules that get Capsa, or a local build version for Capsa developers]"
---

# capsa-setup

Add Capsa to this project. The user’s request:

> $ARGUMENTS

If the line above shows nothing, or the placeholder as it is, take the request from the user’s message that called this command. An empty request means: the whole setup, modules picked with the user.

Show every change to a file of the project before you make it, and make it only after the user agrees. Group small changes into one question. If the user agreed in advance, show the changes in the report instead.

## Start

1. Find the platform of the project. Gradle build files that apply the Kotlin Multiplatform plugin (`org.jetbrains.kotlin.multiplatform`, `kotlin("multiplatform")`) or an Android plugin (`com.android.application`, `com.android.library`, `com.android.kotlin.multiplatform.library`) mean Kotlin. Look in `settings.gradle.kts`, the `build.gradle.kts` files, and `gradle/libs.versions.toml`.
2. If there are no Gradle build files, the platform is something else, or you can’t tell, say that Capsa doesn’t support this project yet, name what you found, and stop. Don’t guess.
3. Load the `capsa` skill and read its `SKILL.md`, then the platform file: `references/kotlin.md` for Kotlin. Before the setup the skill may still be missing: then read the platform file straight from GitHub, `https://raw.githubusercontent.com/MairwunNx/Capsa/master/plugins/capsa/skills/capsa/references/kotlin.md`.

## 1. Look at the project

- **Kotlin version.** Capsa needs Kotlin 2.4 or newer. On an older one, tell the user and stop: updating Kotlin is the user’s decision.
- **Capsa already there.** Search the build files for `com.mairwunnx.capsa`. If Capsa is in place, do only the steps that are missing and say which ones you skipped.
- **Modules.** Find the modules with screens. Ask the user which ones get Capsa, unless the request names them.
- **How the project adds libraries:** a version catalog, versions in the build files, or a convention plugin. Follow the same way.

## 2. Find the version

Take the latest release of `capsa-bom` from Maven Central: `<release>` in `https://repo1.maven.org/maven2/com/mairwunnx/capsa/capsa-bom/maven-metadata.xml`, or the latest release of `MairwunNx/Capsa` on GitHub. Never guess the version. If neither answers, tell the user and stop.

**A local build, for Capsa developers.** When the user asks for a local build of Capsa (`./gradlew publishToMavenLocal` in `kotlin/` of the Capsa repository), use the version the user names and add the local repository before the others, for Capsa only:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroup("com.mairwunnx.capsa") } }
        // the repositories the project already has
    }
}
```

Tell the user to remove it before a release.

## 3. Add the dependencies

Use “Modules and coordinates” in `references/kotlin.md`:

- **Versions:** the Capsa version and its libraries go into the version catalog, or wherever the project keeps versions.
- **Main code:** `capsa-compose` for screens on `rememberCapsa` or with `CollectEffects`. `capsa-viewmodel` for screens on `CapsaViewModel` or a UI without Compose. `capsa-core` for a module with capabilities only.
- **Tests:** `capsa-test` in the test source set of the same modules.
- **Serialization:** the Kotlin serialization plugin, only if the project will save slices with `installSaved` and doesn’t apply the plugin yet.

Check that the project resolves from `mavenCentral()`, unless Capsa comes from a local build. Don’t add repositories the project doesn’t need.

If the user hasn’t picked a host yet, choose the artifact from the screens as they are now: view models mean `capsa-viewmodel`, Compose screens without view models mean `capsa-compose`. Say that `capsa-migrate` and `capsa-new` ask for the host of each screen and may add the other artifact.

## 4. Build

Sync and build the changed modules with the narrowest task that compiles them, such as `:<module>:compileKotlinJvm` or `:<module>:assembleDebug`. Find the project’s commands as `references/kotlin.md` describes, and don’t guess. Check that the test dependencies resolve too, for example by compiling the tests or listing the test classpath, since a module without tests compiles nothing there. If the build fails because of the change, fix it. If it fails for another reason, show the error and stop.

## 5. Install the skills

Check whether the agent sees the `capsa` skill. If the five `capsa` skills already sit in the project’s skills folder, say so and go on.

- **Claude Code:** if this command runs from the `capsa` plugin, the skills are in place. Otherwise offer the plugin, with the user’s consent:

  ```sh
  claude plugin marketplace add MairwunNx/Capsa
  claude plugin install capsa@capsa
  ```

- **Other harnesses,** or Claude Code without the plugin: copy the folders of `plugins/capsa/skills/` from `MairwunNx/Capsa` into the skills folder of the project that this harness reads. Take the folder from the harness’s documentation, don’t guess it. Put the skills into one folder only: some harnesses load a skill twice when two of their folders hold it.
- **A harness without skills:** create a thin rules file in the format of that harness, with one pointer to the `capsa` skill in `MairwunNx/Capsa` and no copy of the rules. Only with the user’s consent.

## 6. Point the agents to the skill

Add one line to the instructions file the project already uses: `AGENTS.md`, `CLAUDE.md`, or `GEMINI.md`. If one of them is a link to another, write to the file it points to. The line:

```markdown
- Screens on Capsa follow Capability Slice Architecture: load the `capsa` skill before you write, change, or review them.
```

Put it into the list of rules if the file has one, otherwise at the end of the file. Create a new instructions file only with the user’s consent.

## 7. Report and next step

Report briefly: the version, the modules and files you changed, how the build went, where the skills are, and which instructions file got the line. Then offer to move the first screen with `capsa-migrate` or to create one with `capsa-new`.

## Example requests

- `/capsa-setup`
- `/capsa-setup add Capsa to :feature:search and :feature:profile`
- `/capsa-setup use my local build 0.99.0-SNAPSHOT from mavenLocal`
