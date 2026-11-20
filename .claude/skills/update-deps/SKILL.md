---
name: update-deps
description: Updates the dependencies of Capsa, a Kotlin Multiplatform library, to their newest stable versions, while minSdk, the Kotlin API and language version, and the iOS deployment target stay as they are. Covers Gradle, Kotlin, AGP, the Gradle plugins, kotlinx, AndroidX, Compose Multiplatform, Robolectric with compileSdk, and GitHub Actions. Use when the owner asks to update dependencies, libraries, or tools, and in the weekly autopilot.
argument-hint: "[only these, e.g. gradle kotlin]"
metadata:
  internal: true
---

# Update dependencies

Bring every pinned version in Capsa up to the newest stable one, prove that the library still builds, passes its tests, and runs as fast as before, and commit. `sources.md` next to this file says where each version comes from.

If the owner named only some dependencies (`$ARGUMENTS`), update just those.

## What stays where it is

Users pick Capsa by what it asks of their project. These limits decide who can add Capsa, so only the owner moves them:

- `android-minSdk` in `kotlin/gradle/libs.versions.toml`.
- The Kotlin `apiVersion` and `languageVersion`, when a build script sets them. Without them they follow the compiler, so a new Kotlin raises what users need: say so in the report.
- The iOS deployment target of the demo app and of any framework the build makes.
- `jvm-toolchain`: the bytecode level users on the JVM and Android need.

The tools move freely: Gradle, the Kotlin compiler, AGP, Dokka, kover, detekt, ktlint, the binary compatibility validator, kotlinx-benchmark, the publish plugin, and the foojay resolver in `settings.gradle.kts`.

## compileSdk moves with Robolectric

The host tests of `capsa-compose` run Robolectric on the SDK from `android-compileSdk`. A `compileSdk` newer than the current Robolectric knows fails those tests. So the two move together:

1. Update `robolectric` to its newest stable version first.
2. Raise `android-compileSdk` only to a stable platform that this Robolectric supports. Its release notes say which, and `./gradlew :capsa-compose:testAndroidHostTest` proves it: an unknown SDK fails at once with a message about `maxSdkVersion`.
3. If no stable Robolectric supports the new SDK yet, `compileSdk` stays, and the report says so.
4. If the new SDK needs a newer Java under Robolectric, raise `robolectric-jvm` in the catalog.

## Libraries users get

coroutines, serialization, atomicfu, lifecycle, savedstate, Compose Multiplatform, `androidx.tracing`, and `androidx.startup` land in the POMs of Capsa, so a user’s build resolves them. They move to the newest stable version like the rest. Two pairs follow another library:

- `androidx-lifecycle-process` comes from Google, while `androidx-lifecycle` is JetBrains’ build. Take the Google version that JetBrains’ `lifecycle-viewmodel` depends on for Android: its `.module` file on Maven Central names it.
- `androidx-compose-runtime-retain` stays on the version `compose-ui` of the new Compose Multiplatform pulls, as the comment in the catalog says.

## Steps

1. **Read CLAUDE.md.** Then run `git status`: leave other people’s uncommitted changes alone and keep them out of your commits.
2. **List every pinned version:** `kotlin/gradle/libs.versions.toml`, `kotlin/gradle/wrapper/gradle-wrapper.properties`, the `plugins` block of `kotlin/settings.gradle.kts`, every `build.gradle.kts` under `kotlin/` (`samples/` too), `kotlin/build-logic/`, the Xcode project of the demo when there is one, and `uses:` in `.github/workflows/`.
3. **Find each newest version** where `sources.md` says to ask. Note old and new side by side. If nothing is behind, say so and stop: no commit, no changelog, no release.
4. **Measure before.** In `kotlin/`, run `./gradlew :capsa-benchmarks:jvmGcBenchmark` on the old versions and keep the result as a baseline outside the repository: `python3 -I ../tools/bench_compare.py --write-baseline --baseline <scratch>/before.json`. Comparing on the same machine says more than any committed baseline.
5. **Update in groups:** Gradle, then Kotlin with the plugins that follow it, then AGP with Robolectric and `compileSdk`, then the libraries users get, then the build tools (Dokka, kover, detekt, ktlint, the validator, kotlinx-benchmark, the publish plugin), then Actions. After each group build and test it as “Check” says, so a failure points at its cause.
6. **Majors:** read the release notes or the migration guide and fix the code. If a major breaks too much to fix well, keep the old major and say so in the report.
7. **Check** everything, as below.
8. **Changelog,** as below.
9. **Commit and push,** one commit per group, as CLAUDE.md’s “Commits” says: `build(deps): kotlin 2.4.30 and compose multiplatform 1.12.3`, `ci(deps): actions/checkout v8`. Before the push run `git fetch origin` and `git pull --rebase` when `master` moved.

## Check

All of it, after the last group:

- `cd kotlin && ./gradlew build`: every target, the tests with `macosArm64Test`, detekt, ktlint, the API dumps, and the demo app in `samples/`. A dump that changed only because a dependency moved is fine: `./gradlew apiDump`, and the diff goes into the report. A change of Capsa’s own API is not: undo it.
- iOS: `./gradlew linkDebugTestIosSimulatorArm64 compileTestKotlinIosArm64`, as CI runs it. With an iOS simulator runtime on the machine, `iosSimulatorArm64Test` runs the tests too. When the demo has an Xcode project, build it with `xcodebuild` for the simulator.
- `./gradlew :koverVerify`: the coverage bound.
- Benchmarks: `./gradlew :capsa-benchmarks:jvmGcBenchmark`, then `python3 -I ../tools/bench_compare.py --baseline <scratch>/before.json`. A benchmark more than 25 % slower than before points at the group that slowed it: find the dependency by moving it back, keep that one on the old version, and report it as ⚡ with the numbers.

**detekt and ktlint stay as strict as they are.** A new version that finds new problems means fixing the code, never a rule switched off, a baseline file, or a `@Suppress`.

## Changelog

Only an update that reaches Maven Central needs a changelog: a new Kotlin, AGP, or `compileSdk`, a library users get, or the Dokka or publish plugin that shapes the published files. Tests, benchmarks, linters, Gradle, and Actions don’t reach users.

- When the topmost `## X.Y.Z` section of `CHANGELOG.md` is released (`gh release view vX.Y.Z` succeeds), add a new section on top with the next patch version.
- When it isn’t released yet, add the line to it and don’t release it: that version is the owner’s.

The line follows the style of the file, an emoji and a **bold** first phrase: `- ⬆️ **Fresh dependencies.** Builds on Kotlin 2.4.30, coroutines 1.11.1, Compose Multiplatform 1.12.3.` Name each library users get that moved. When `minSdk` or what a user needs changed, say so in its own line.

## Running unattended

The autopilot (“Autopilot” in CLAUDE.md) runs this every Monday in GitHub Actions on `macos-latest`, with nobody to answer. Where a step would ask the owner, take the choice that keeps Capsa as it works today, and name it in the report. A failure you can’t fix stays out of the commit: keep the old version and go on with the rest.

The autopilot’s token can’t push a change to `.github/workflows/`: GitHub refuses it without the `workflow` scope. In an autopilot run, leave Actions as they are and list the ones behind in the report. The owner updates them by hand.

## Report

A table: each dependency, old version, new version. Then what stayed behind and why, the benchmark comparison, and how the check went.
