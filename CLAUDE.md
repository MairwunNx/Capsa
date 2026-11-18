# Capsa: notes for AI agents

📦 Capsa is a Kotlin Multiplatform engine for screens built from small capabilities. Each capability owns one slice of the screen’s state and declares its async work as data: the runtime starts that work, cancels it, and drops stale results. Side effects leave through a single door. Capsa runs on Android, iOS, and the JVM. README.md is the shop window, README.RU.md its Russian copy. The manual is the GitHub wiki (https://github.com/MairwunNx/Capsa/wiki).

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
| `kotlin/` | The Kotlin Multiplatform implementation, its own Gradle build: `capsa-core`, `capsa-viewmodel`, `capsa-compose`, `capsa-test`, `capsa-bom`, the benchmarks, and the demo app in `samples/` |
| `plugins/capsa/` | What users install for their agents: the `capsa` skill and the `capsa-setup`, `capsa-migrate`, `capsa-new`, `capsa-review` commands |
| `.claude-plugin/` | The marketplace that lists the plugin |
| `.claude/skills/` | Skills for working on this repository, such as `update-deps`. Users never see them |
| `docs/` | Pictures for the README and the wiki: `images/` (SVG in light and dark), `media/` (WebP in git, mp4 in LFS), `kdoc/`, `social/` |
| `tools/` | Helper scripts: diagrams, skill install |
| `.github/workflows/` | `build.yml` on every push, `release.yml`, `autopilot.yml` |

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

## Hard rules

- **English in code.** Identifiers, KDoc, error messages, and test names are in English.
- **Explicit API.** Every module builds in explicit API mode: each public declaration states its visibility and its type, and carries KDoc.
- **Code style.** Kotlin uses 4 spaces, YAML, JSON, and Markdown use 2. Lines stay within 186 columns. `.editorconfig` holds the rest.
- **Commits** follow `type(scope): short summary`: English, lower case, up to 72 characters. Types are `feat`, `fix`, `docs`, `test`, `build`, `ci`, `chore`, `refactor`. The body is one or two short lines or nothing, without trailers. One logical change per commit, and each commit builds.
- **Clean history.** Code, docs, and commits name no employer, no internal project, no ticket numbers, no local paths. Examples use domains anyone knows: a counter, a search, a like, a timer, a form.
- **Versions** come from their sources, never from memory: always the latest stable Kotlin, Gradle, AGP, Compose Multiplatform, AndroidX, kotlinx, and actions.
- **Secrets** come from the owner’s secret store. Never print a secret’s value or write it into a file. Before publishing anywhere or setting secrets, ask the owner.

## Releases

To be written: the release workflow, the version, and `CHANGELOG.md`.

## Autopilot

To be written: the autopilot workflow and the `update-deps` skill.
