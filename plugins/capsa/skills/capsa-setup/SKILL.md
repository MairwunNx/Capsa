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
3. Load the `capsa` skill and read its `SKILL.md`, then the platform file: `references/kotlin.md` for Kotlin. Before the setup the skill may still be missing: then read both files straight from GitHub, `https://raw.githubusercontent.com/MairwunNx/Capsa/master/plugins/capsa/skills/capsa/SKILL.md` and `https://raw.githubusercontent.com/MairwunNx/Capsa/master/plugins/capsa/skills/capsa/references/kotlin.md`.

## 1. Look at the project

- **Kotlin version.** Capsa needs Kotlin 2.4 or newer. On an older one, tell the user and stop: updating Kotlin is the user’s decision.
- **Targets.** Compare the targets of each module that gets Capsa with the targets in `references/kotlin.md`. Capsa has no `js` and no `wasmJs`, so a module that builds for them can’t take Capsa in `commonMain`. Tell the user and offer a shared source set for the supported targets, such as `nonWebMain` between `commonMain` and the Android, iOS, and JVM source sets, where the Capsa code and its dependencies live. Don’t drop a target on your own.
- **Capsa already there.** Search the build files for `com.mairwunnx.capsa`. If Capsa is in place, do only the steps that are missing and say which ones you skipped.
- **Modules.** Find the modules with screens. Ask the user which ones get Capsa, unless the request names them.
- **How the project adds libraries:** a version catalog, versions in the build files, or a convention plugin. Follow the same way.

## 2. Find the version

Pick the version as “Modules and coordinates” in `references/kotlin.md` describes: the one the project already pins, or the latest release when the project has no Capsa yet. If you need the latest release and neither source answers, tell the user and stop.

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

Check whether the agent sees the `capsa` skill. If the five `capsa` skills already sit in a skills folder of the project or of the user, say where and go on.

Find out which harnesses the project uses: ask the user, or look for their files (`.claude/`, `CLAUDE.md`, `.opencode/`, `opencode.json`, `.codex/`, `.gemini/`, `GEMINI.md`, `.cursor/`, `AGENTS.md`). Then install the skills in the way of this table, with the user’s consent:

| Harness | Where the skills go | How the user calls a command |
|---|---|---|
| Claude Code | The plugin: `claude plugin install capsa --marketplace MairwunNx/Capsa` | `/capsa:capsa-migrate` with the plugin, `/capsa-migrate` from `.claude/skills/` |
| OpenCode | `.agents/skills/` | `/capsa-migrate` |
| Codex | `.agents/skills/` | `$capsa-migrate`, or pick it in `/skills` |
| Gemini CLI | `.agents/skills/` | Ask for it by name: “use the capsa-migrate skill to …”. Gemini asks to activate the skill |
| Cursor and Cursor CLI | `.agents/skills/` | `/capsa-migrate` |

- **Claude Code gets the plugin.** Offer it first, always. Copy the skills into `.claude/skills/` only when the user declines the plugin or plugins are off, for example by the settings of an organization. Claude Code older than 2.1.275 has no `--marketplace`: there, run `claude plugin marketplace add MairwunNx/Capsa`, then `claude plugin install capsa@capsa`.
- **Copy the skills** for the other harnesses `npx skills add MairwunNx/Capsa/plugins/capsa/skills`, or with `curl -fsSL https://raw.githubusercontent.com/MairwunNx/Capsa/master/tools/install-skills.sh | sh -s -- --agents` (`.agents/skills/`, without `--agents` it fills `.claude/skills/`). Without either, copy the folders of `plugins/capsa/skills/` from `MairwunNx/Capsa` by hand. When `npx skills` asks for the agents, leave out Claude Code if it has the plugin: for Claude Code it links the skills into `.claude/skills/` as well.
- **One folder per project.** OpenCode and Cursor read both `.claude/skills/` and `.agents/skills/`. When both folders hold the skills, OpenCode warns about every name, and Cursor may load each skill twice. When the team uses Claude Code and another harness, take the plugin for Claude Code and `.agents/skills/` for the rest: the plugin lives outside the project, so nothing doubles. If the user declined the plugin, `.claude/skills/` serves Claude Code, OpenCode, and Cursor, while Codex and Gemini CLI need `.agents/skills/`: name the trade-off and let the user choose.
- **For one user instead of the project:** `~/.claude/skills/` for Claude Code, `~/.agents/skills/` for the others.
- **A harness that reads no skills folder:** create a thin rules file in the format of that harness, with one pointer to the `capsa` skill in `MairwunNx/Capsa` and no copy of the rules. Only with the user’s consent.

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
