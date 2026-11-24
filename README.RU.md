# 📦 Capsa <sup>превью</sup>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.mairwunnx.capsa/capsa-core"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/com.mairwunnx.capsa/capsa-core?label=Maven%20Central"></a>
  <a href="https://github.com/MairwunNx/Capsa/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/MairwunNx/Capsa/build.yml?branch=master&label=build"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/MairwunNx/Capsa"></a>
  <img alt="Kotlin 2.4" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Android | iOS | macOS | JVM" src="https://img.shields.io/badge/Android%20%7C%20iOS%20%7C%20macOS%20%7C%20JVM-3DDC84?logo=kotlin&logoColor=white">
  <img alt="Compose Multiplatform" src="https://img.shields.io/badge/Compose%20Multiplatform-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Languages: English, Russian" src="https://img.shields.io/badge/languages-en%20%7C%20ru-blue">
</p>

📦 **Capsa** — движок Kotlin Multiplatform для экранов из маленьких capability. Каждая capability владеет своим slice состояния экрана и описывает асинхронную работу как данные: runtime запускает эту работу, отменяет её и отбрасывает устаревшие ответы.

Capsa реализует [Capability Slice Architecture](https://github.com/MairwunNx/Capability-Slice-Architecture) — паттерн для клиентских приложений, вдохновлённый [Feature-Action Architecture](https://github.com/MairwunNx/Feature-Action-Architecture).

[English version](README.md)

> [!NOTE]
> До `1.0.0` Capsa — превью, и API ещё может измениться. [CHANGELOG.md](CHANGELOG.md) описывает, что приносит каждая версия.

UI отправляет интент, capability решает, что он значит для её slice, а остальное делает runtime: сохраняет slice, запускает работу и передаёт эффекты хосту.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/flow-dark.svg">
  <img alt="Как факт проходит через экран Capsa: UI отправляет интент, runtime передаёт его capability, decide возвращает Decision, работа отвечает сигналом, проекция превращает slice в UI-модель, а хост выполняет эффекты" src="docs/images/flow-light.svg">
</picture>

<table>
  <tr>
    <td align="center" width="50%"><a href="docs/media/search-android.mp4"><img src="docs/media/search-android.webp" width="300" alt="Пример поиска на Android: «berry» набирается по букве, и результаты приходят после короткой паузы"></a><br><sub>Поиск: запрос ждёт паузы в наборе, и каждая новая буква отменяет прежний</sub><br><br><a href="docs/media/search-android.mp4"><img src="docs/media/watch-ru.png" height="40" alt="Смотреть видео"></a></td>
    <td align="center" width="50%"><a href="docs/media/like-ios.mp4"><img src="docs/media/like-ios.webp" width="300" alt="Пример лайка на iOS: сервер отключается, сердце закрашивается сразу после нажатия, а потом лайк откатывается со снекбаром"></a><br><sub>Лайк: сердце закрашивается сразу и откатывается, когда сервер не ответил</sub><br><br><a href="docs/media/like-ios.mp4"><img src="docs/media/watch-ru.png" height="40" alt="Смотреть видео"></a></td>
  </tr>
</table>

## Что вы получаете

- 🧩 **Экран из маленьких сценариев.** Лайк, поиск и таймер живут каждый в своей capability, со своим slice. Правка одного сценария не задевает остальные.
- ⏳ **Отмена и устаревшие ответы — забота runtime.** Capability возвращает работу с ключом. Runtime запускает её, отменяет прежний запрос при следующем нажатии и отбрасывает его запоздавший ответ.
- ⏱️ **Debounce и retry одним аргументом.** `debounce = 300.milliseconds` ждёт паузы в наборе, а `Retry(times = 3, delay = 1.seconds)` повторяет упавший вызов.
- 🚪 **Побочные эффекты выходят через одну дверь.** Навигация, снекбары и аналитика покидают логику как эффекты, и выполняет их только хост.
- 🛟 **Debug падает, release живёт.** Нарушенный контракт сразу роняет debug-сборку. Release-сборка пишет его в лог вместе с последними 32 событиями runtime, и экран работает дальше.
- 🧪 **Тесты без Android и iOS.** `capsa-test` запускает настоящий runtime на виртуальном времени в обычном `commonTest`: вы отправляете факты и проверяете slice и эффекты.
- 📱 **Android и iOS из одного кода.** Экраны на Compose Multiplatform работают на Android, iOS, macOS и десктопе, а хостом служит сама композиция или `ViewModel`.
- 🤖 **Ваш агент знает Capsa.** Скиллы для Claude Code, OpenCode, Codex, Gemini CLI и Cursor подключают Capsa к проекту, переводят на неё экран, пишут новые capability и проверяют код на правила.

## Как начать

### 1. Добавьте зависимость

Capsa лежит в Maven Central. BOM выравнивает версии её модулей, так что возьмите самую новую с бейджа выше:

```kotlin
// build.gradle.kts of the screen module
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("com.mairwunnx.capsa:capsa-bom:0.99.0"))
            implementation("com.mairwunnx.capsa:capsa-compose")
        }
        commonTest.dependencies {
            implementation("com.mairwunnx.capsa:capsa-test")
        }
    }
}
```

Capsa нужен Kotlin 2.4 или новее, а работает она на Android 6.0 (API 23) и новее, iOS, macOS на Apple silicon и JVM 17. Таргетов `js` и `wasmJs` у неё нет. Все ограничения перечислены на странице [Requirements](https://github.com/MairwunNx/Capsa/wiki/Requirements), а [Installation](https://github.com/MairwunNx/Capsa/wiki/Installation) рассказывает о version catalog и о модуле, который собирается ещё и для веба.

### 2. Напишите экран

Capability держит логику одного сценария. Здесь она владеет двумя интентами и на каждый отвечает решением:

```kotlin
data class CounterSlice(val count: Int = 0)

sealed interface CounterIntent : Intent
data object PlusClicked : CounterIntent
data object MinusClicked : CounterIntent

context(_: CapabilityScope)
fun counter() = capability<CounterSlice, CounterIntent, Nothing>(name = "counter", initial = CounterSlice()) {
    onIntent<PlusClicked> { state, _ -> Decision(state = state.copy(count = state.count + 1)) }
    onIntent<MinusClicked> { state, _ -> Decision(state = state.copy(count = state.count - 1)) }
}
```

`rememberCapsa` держит capability прямо в композиции, без view model:

```kotlin
@Composable
fun CounterScreen() {
    val screen = rememberCapsa { expose(install(counter())) }
    val state by screen.state.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { screen.send(MinusClicked) }) { Text("−") }
        Text("${state.count}")
        Button(onClick = { screen.send(PlusClicked) }) { Text("+") }
    }
}
```

Привыкли к `ViewModel`? Добавьте `capsa-viewmodel`, и тот же экран живёт в `class CounterViewModel : CapsaViewModel() { val state = install(counter()) }`.

Страница [Getting started](https://github.com/MairwunNx/Capsa/wiki/Getting-started) идёт дальше: тест на harness, эффекты и как выбрать хост.

### 3. Или поручите это агенту

Дайте своему агенту одну строку:

```text
Add Capsa to this project: follow https://raw.githubusercontent.com/MairwunNx/Capsa/master/SETUP.md
```

<a href="https://cursor.com/link/prompt?text=Add%20Capsa%20to%20this%20project%3A%20follow%20https%3A%2F%2Fraw.githubusercontent.com%2FMairwunNx%2FCapsa%2Fmaster%2FSETUP.md"><img alt="Open in Cursor" src="https://img.shields.io/badge/Open%20in-Cursor-000000?logo=cursor&logoColor=white"></a>

Агент найдёт самую новую версию, подключит Capsa к сборке, поставит скиллы и предложит перевести первый экран. Каждую правку файла он сначала покажет вам.

Чтобы поставить скиллы самим:

- **Claude Code:** `claude plugin install capsa --marketplace MairwunNx/Capsa`, затем `/capsa:capsa-setup`.
- **OpenCode, Codex, Gemini CLI, Cursor и Cursor CLI:** `npx skills add MairwunNx/Capsa/plugins/capsa/skills` кладёт их в `.agents/skills/`. Без Node.js: `curl -fsSL https://raw.githubusercontent.com/MairwunNx/Capsa/master/tools/install-skills.sh | sh -s -- --agents`.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/install-dark.svg">
  <img alt="Три способа поставить скиллы Capsa: плагин Claude Code, npx skills add в .agents/skills или копия папок. Каждый приносит те же пять скиллов: capsa, capsa-setup, capsa-migrate, capsa-new и capsa-review" src="docs/images/install-light.svg">
</picture>

Затем вызывайте `capsa-setup`, `capsa-migrate`, `capsa-new` или `capsa-review`. Как их вызывает каждый харнесс, показывает страница [AI agents](https://github.com/MairwunNx/Capsa/wiki/AI-agents).

## Документация

Руководство живёт в [вики](https://github.com/MairwunNx/Capsa/wiki), на английском.

**Разработчикам приложений:** [Installation](https://github.com/MairwunNx/Capsa/wiki/Installation) · [Requirements](https://github.com/MairwunNx/Capsa/wiki/Requirements) · [Getting started](https://github.com/MairwunNx/Capsa/wiki/Getting-started) · [Concepts](https://github.com/MairwunNx/Capsa/wiki/Concepts) · [Capabilities and slices](https://github.com/MairwunNx/Capsa/wiki/Capabilities-and-slices) · [Hosting](https://github.com/MairwunNx/Capsa/wiki/Hosting) · [Work and cancellation](https://github.com/MairwunNx/Capsa/wiki/Work-and-cancellation) · [Streams](https://github.com/MairwunNx/Capsa/wiki/Streams) · [Debounce and retries](https://github.com/MairwunNx/Capsa/wiki/Debounce-and-retries) · [Effects](https://github.com/MairwunNx/Capsa/wiki/Effects) · [Projections](https://github.com/MairwunNx/Capsa/wiki/Projections) · [Ports and wiring](https://github.com/MairwunNx/Capsa/wiki/Ports-and-wiring) · [Saving state](https://github.com/MairwunNx/Capsa/wiki/Saving-state) · [Errors and crash free release](https://github.com/MairwunNx/Capsa/wiki/Errors-and-crash-free-release) · [Communication](https://github.com/MairwunNx/Capsa/wiki/Communication) · [Testing](https://github.com/MairwunNx/Capsa/wiki/Testing) · [Observability](https://github.com/MairwunNx/Capsa/wiki/Observability) · [Migrating a screen](https://github.com/MairwunNx/Capsa/wiki/Migrating-a-screen) · [AI agents](https://github.com/MairwunNx/Capsa/wiki/AI-agents) · [FAQ](https://github.com/MairwunNx/Capsa/wiki/FAQ)

**Тем, кто развивает Capsa:** [Releasing](https://github.com/MairwunNx/Capsa/wiki/Releasing) · [Сборка и проверка](CLAUDE.md#build-and-check) · [Демо-приложение](kotlin/samples) · [Видео демо](tools/media/README.md) · [CHANGELOG.md](CHANGELOG.md)

## Связанные проекты

- **[Capability Slice Architecture](https://github.com/MairwunNx/Capability-Slice-Architecture)** — спецификация, которую реализует Capsa: словарь, пять правил и гарантии, которые обязана дать каждая реализация.
- **[Feature-Action Architecture](https://github.com/MairwunNx/Feature-Action-Architecture)** — моя архитектура для бэкендов, из которой выросла CSA.

> 🫡 Made by Pavel Erokhin (Павел Ерохин), aka mairwunnx.
