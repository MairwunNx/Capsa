# Capsa: notes for AI agents

📦 Capsa is a Kotlin Multiplatform engine for screens built from small capabilities. Each capability owns one slice of the screen’s state and declares its async work as data: the runtime starts that work, cancels it, and drops stale results. Side effects leave through a single door. Capsa runs on Android, iOS, macOS, and the JVM. README.md is the shop window, README.RU.md its Russian copy. The manual is the GitHub wiki (https://github.com/MairwunNx/Capsa/wiki).

## Capsa and Capability Slice Architecture

Capability Slice Architecture (CSA, https://github.com/MairwunNx/Capability-Slice-Architecture) is the specification: principles, vocabulary, rules, and the guarantees every implementation owes. Capsa is its implementation, and it follows every rule of CSA. Capsa holds more than the spec: the API, the hosts, the test harness, the tools. So Capsa’s docs and skills may repeat facts of the spec, but when the two disagree, CSA wins and Capsa gets fixed.

## Talking to the owner

- Reply in Russian and address the owner as «вы». Quotes «ёлочки», nested „лапки“, an em dash with spaces « — ».
- Code, KDoc, comments, identifiers, commit messages, the wiki, and the skills are in English.
- English prose uses curly quotes “ ” and ’, and an en dash with spaces ( – ), never an em dash. No semicolons between clauses.

## How the repository is laid out

The root holds what every implementation shares. Each implementation gets its own folder.

| Path | What lives there |
|---|---|
| `kotlin/` | The Kotlin Multiplatform implementation, its own Gradle build: `capsa-core`, `capsa-host`, `capsa-viewmodel`, `capsa-compose`, `capsa-test`, `capsa-viewmodel-test`, `capsa-detekt`, `capsa-bom`, the benchmarks (`capsa-benchmarks`, `capsa-benchmarks-android`), and the demo app with its macrobenchmarks in `samples/` |
| `plugins/capsa/` | What users install for their agents: the `capsa` skill and the `capsa-setup`, `capsa-migrate`, `capsa-new`, `capsa-review` commands |
| `.claude-plugin/` | The marketplace that lists the plugin |
| `SETUP.md` | What an agent does to add Capsa to a project. The starter prompt of the README points to its raw URL |
| `ctxray/`, `ctxray.config.ts`, `ctxray.lock` | The ctxray tests of the skills, the projects they run on, and the pinned harness builds |
| `.claude/skills/` | Skills for working on this repository, such as `update-deps`. Users never see them |
| `docs/` | Pictures for the README and the wiki: `images/` (SVG in light and dark), `media/` (WebP in git, mp4 in LFS), `kdoc/`, `social/` |
| `tools/` | Helper scripts: diagrams, the “Watch the video” buttons, skill install, benchmark comparison, benchmarks on a phone, the baseline profile |
| `.github/workflows/` | `build.yml` on every push, `ctxray.yml` on pushes to the skills, `release.yml`, `autopilot.yml` |

Later implementations stand next to `kotlin/` and don’t change the root:

- `dotnet/`: an F# core with Avalonia.FuncUI.
- `swift/`: native Swift with SwiftUI. SwiftPM only takes a package from git when `Package.swift` sits in the repository root, so that one file goes to the root and the sources stay in `swift/`.

Neither folder exists yet.

Open the repository root in IntelliJ IDEA or Android Studio, not `kotlin/`. The shared `.idea/gradle.xml` links the Gradle build in `kotlin/`, so the IDE imports it on its own, and the shared code style in `.idea/codeStyles/` applies. VS Code opens `capsa.code-workspace`.

## Build and check

```sh
cd kotlin && ./gradlew build
```

`build` compiles every target, runs the tests, detekt, and ktlint. A commit that fails it doesn’t go in.

- **Public API.** `build` also compares the public API with the dumps in each module’s `api/` folder. After you change the API on purpose, run `./gradlew apiDump` and commit the new dumps with the change.
- **Xcode.** Kotlin/Native needs the full Xcode, not only the Command Line Tools, to link tests such as `macosArm64Test`. If `xcodebuild -version` fails, run `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`. `iosSimulatorArm64Test` also needs the iOS simulator runtime: `xcodebuild -downloadPlatform iOS`. CI skips the simulator tests (it sets `CI`) and relies on `macosArm64Test`.
- **Skills.** ctxray checks the skills of `plugins/capsa` on five agent harnesses: `npx @mairwunnx/ctxray test --layer L1-L5`. `ctxray.yml` runs L1–L5 on every push to the skills and L6–L7 on Claude Code with the answers recorded in `ctxray/__cassettes__`. A run by hand in Actions asks the real model when the repository holds `CTXRAY_CLAUDE_CODE_OAUTH_TOKEN` or `CTXRAY_ANTHROPIC_API_KEY`. After you change the skills on purpose, commit the fresh answers from its artifact to `ctxray/__cassettes__`.

## Hard rules

- **English in code.** Identifiers, KDoc, error messages, and test names are in English.
- **Explicit API.** Every module builds in explicit API mode: each public declaration states its visibility and its type, and carries KDoc.
- **Code style.** Kotlin uses 4 spaces, YAML, JSON, and Markdown use 2. Lines stay within 186 columns. `.editorconfig` holds the rest.
- **Commits** follow `type(scope): short summary`: English, lower case, up to 72 characters. Types are `feat`, `fix`, `docs`, `test`, `build`, `ci`, `chore`, `refactor`, `perf`. The body is one or two short lines or nothing, without trailers. One logical change per commit, and each commit builds.
- **Clean history.** Code, docs, and commits name no employer, no internal project, no ticket numbers, no local paths. Examples use domains anyone knows: a counter, a search, a like, a timer, a form.
- **Versions** come from their sources, never from memory: always the latest stable Kotlin, Gradle, AGP, Compose Multiplatform, AndroidX, kotlinx, and actions.
- **Secrets** come from the owner’s secret store. Never print a secret’s value or write it into a file. Before publishing anywhere or setting secrets, ask the owner.

## Benchmarks

`kotlin/capsa-benchmarks` measures the hot paths of the runtime with kotlinx-benchmark: a fact through one and ten capabilities, `decide` with 1, 10, and 30 reactions, an effect through 1, 10, and 30 handlers, `WorkPolicy.Latest` under fast typing, a `stream` of 10 000 signals, a violation report, and `combineState` over three slices. The numbers tell the order of magnitude and catch a release that got slower. They don’t compare Capsa with anything else.

| Command (in `kotlin/`) | What it does |
|---|---|
| `./gradlew :capsa-benchmarks:benchmark` | The full run on the JVM and on `macosArm64`. The tables land in the console, the JSON in `capsa-benchmarks/build/reports/benchmarks/main/` |
| `./gradlew :capsa-benchmarks:smokeBenchmark` | One short iteration of every benchmark. CI runs it on every push, so the benchmarks keep compiling and running. Its numbers mean nothing |
| `./gradlew :capsa-benchmarks:jvmGcBenchmark` | The full JVM run with the JMH GC profiler, in `capsa-benchmarks/build/reports/benchmarks/gc/jvm.json`. The comparison reads this one |
| `python3 tools/bench_compare.py` | Compares the last `jvmGcBenchmark` run with `capsa-benchmarks/baseline/jvm.json` and fails when any benchmark got more than 25 % slower |

**How to read them.** The mode is throughput: operations per millisecond, more is better. `Alloc` is bytes allocated per operation, from `gc.alloc.rate.norm`. The `±` error is the spread between iterations: a difference smaller than the error is noise. A change in allocations is exact and doesn’t depend on the machine, a change in speed does. The comparison warns when the baseline comes from another processor or JDK.

**When to update the baseline.** `baseline/jvm.json` comes from the owner’s machine, and `python3 tools/bench_compare.py` compares a local run with it. The release runs on a GitHub runner and compares with the baseline of that runner’s processor, `baseline/jvm-<cpu>.json`. When the runner has none yet, the release writes it instead of comparing and commits it to `master`, and the next releases compare with it. Update a baseline on purpose only, in its own commit `perf(bench): new baseline after …` with the reason: `python3 tools/bench_compare.py --write-baseline`, with `--baseline` for a runner’s file. Never update it to hide a slowdown.

**When you change the runtime for speed:** measure before, change, measure after on the benchmark of that path, and keep the change only when the numbers show a gain.

### On a phone

The JVM numbers don’t carry over to Android: ART compiles the code differently. `tools/device_bench.sh` measures a phone connected over USB, and CI never runs it. It takes about 20 minutes.

- `kotlin/capsa-benchmarks-android` runs the classes of `capsa-benchmarks` with androidx.benchmark: the median time and the allocated objects per operation.
- `kotlin/samples/macrobenchmark` measures the demo app: the frames of Search under fast typing, of Clock, and of Dashboard, and the cold start. A frame with a positive `frameOverrunMs` came late.
- The results go to `kotlin/capsa-benchmarks-android/results/<device>.json` with the date, the Android version, and the commit. The script compares the microbenchmarks with the previous file and allows 30 %. Commit the new file when it is the new reference.
- `tools/device_bench.sh --profile` also regenerates the baseline profile. `tools/baseline_profile.py` takes the rules of Capsa from the profile of the demo and writes them to `src/androidMain/baselineProfiles/baseline-prof.txt` of `capsa-core`, `capsa-host`, `capsa-viewmodel`, and `capsa-compose`. The AAR carries the file, and the apps that use Capsa compile its hot paths ahead of time. Regenerate it after a change to the hot paths, in its own commit.
- On a phone with Magisk, set Shell to Deny in Magisk’s Superuser list. androidx.benchmark asks `su root id`, and MagiskSU waits forever on it when Shell has root. The script checks this before the run.

The tests that need a real Android run on the phone too: `./gradlew :capsa-viewmodel:connectedAndroidDeviceTest :capsa-compose:connectedAndroidDeviceTest`.

## Releases

One SemVer covers the whole repository: the Maven modules and the plugin for agents. The tag is `vX.Y.Z`. Until `1.0.0` every version is a preview, and the API may still change.

Before a release you may run `tools/device_bench.sh` on a phone. It’s a check by hand, the release doesn’t wait for it.

1. Add a `## X.Y.Z` section to the top of `CHANGELOG.md`. Each item starts with an emoji and a **bold** first phrase about what the user gets, no internals.
2. Run Actions → Release → Run workflow on `master` with the version, or `gh workflow run release.yml -f version=X.Y.Z`. The `changelog` field replaces the section of `CHANGELOG.md` when you fill it.

`release.yml` runs on `macos-latest`, since only macOS builds the iOS artifacts. It checks that the version is new and that `plugin.json` pins no other version, builds and tests the libraries with `-PcapsaVersion`, runs detekt, ktlint, and the coverage bound, then the full benchmarks against the runner’s baseline. A slower release stops there. Then it publishes every module to Maven Central, waits until `repo1.maven.org` serves `capsa-core`, and creates the tag and the GitHub release `Capsa X.Y.Z` with the notes and `capsa-skills.zip`. A `0.x` version goes out as a pre-release. Last, it writes the version into the dependency block of both READMEs and of the wiki’s `Installation` page and pushes them. A version with a suffix such as `1.0.0-rc.1` leaves them alone.

The run needs the repository secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, and `SIGNING_PASSWORD`. Without any of them the build stops before the upload. Before the upload the run asks the Central Portal about the version. It waits for a deployment that Central still validates or publishes instead of uploading again, and it skips the upload once Central has published the version. A failed deployment stops the run: read its errors in the Portal, drop it there, and rerun.

The release leaves the samples out: they ship nowhere, and CI builds them on every push. The libraries’ `androidTarget` still needs the Android SDK, and the `macos-latest` image has one in `ANDROID_HOME`.

The plugin for agents has no `version` in `plugin.json`: Claude Code then takes the commit of the plugin’s folder, and users follow `master`. A version there would pin users to it until the next release.

## Autopilot

`.github/workflows/autopilot.yml` runs Claude every Monday at 03:00 UTC on `macos-latest`, where the iOS and macOS targets build too. Claude runs `/update-deps` (`.claude/skills/update-deps/`): it moves every dependency to its newest stable version, checks the result with the full build, the iOS link, and the benchmarks against a run on the old versions, and pushes to `master`. When the update reaches Maven Central, it writes a new patch section to `CHANGELOG.md`, runs the “Release” workflow with that version, and checks that Central serves every module. This is the owner’s standing go-ahead to release a patch. An unreleased section that was there before stays the owner’s, and the autopilot never releases it.

The run decides alone, and it keeps what limits users where it is: `minSdk`, the Kotlin API and language version, the iOS deployment target, and `jvm-toolchain`. With nothing behind, it ends without a commit. Its token can’t push changes to `.github/workflows/`, so it only lists the Actions that fell behind, and you update them by hand. Its pushes don’t start “Build” either, since GitHub starts no workflow for a push with the workflow’s own token: the run checks the build itself.

The run needs the `CLAUDE_CODE_OAUTH_TOKEN` secret, made by `claude setup-token` on the owner’s subscription, and the release needs the four secrets from “Releases”. When the token expires, the run fails at its first step: make a new one and set it with `gh secret set CLAUDE_CODE_OAUTH_TOKEN`.

Start a run by hand in Actions → Autopilot → Run workflow, or with `gh workflow run autopilot.yml`. Its report sits at the end of the run’s log. To update without the autopilot, run `/update-deps` here, optionally with the names of the dependencies to move.
