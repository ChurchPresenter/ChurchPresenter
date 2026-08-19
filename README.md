This is a Kotlin Multiplatform project targeting Desktop (JVM).

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that's common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple's CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
      folder is the appropriate location.

---

## 🚀 Getting Started

Clone the repository — there are no submodules, so a plain clone is everything you need:
```shell
git clone https://github.com/ChurchPresenter/ChurchPresenter
```

The app is built from several modules that all live in this repository — there are no submodules and
nothing to fetch separately. Four of them are **Gradle modules of this build**, with no wrapper of
their own:

> **[`converter/`](./converter)** — a song/bible format converter built with Compose Desktop,
> accessible from the Help menu. `./gradlew :converter:test`, `./gradlew :converter:packageDmg`.
>
> **[`companion-satellite/`](./companion-satellite)** — a pure-Kotlin Bitfocus Companion Satellite
> protocol client. `./gradlew :companion-satellite:test`.
>
> **[`theme/`](./theme)** — the app's look: the nine color schemes, the semantic color roles, the
> typography and shape scales. `./gradlew :theme:test`.
>
> **[`core-models/`](./core-models)** — the shared data models (schedule items, scenes, questions,
> lyrics). `./gradlew :core-models:test`.
>
> **[`bible-engine/`](./bible-engine)** — the Bible Lookup Engine: speech-to-reference detection.
> `./gradlew :bible-engine:test`.

The other three sit under `composeApp/src/jvmMain/appResources/common/`. Each still keeps its own
Gradle wrapper and test suite, and the first two have their sources compiled into the main app via
`kotlin.srcDir`:

> **ChurchPresenter-LottieGen** — a standalone Compose Desktop app for generating animated
> lower-third overlays as Lottie JSON files, launched from the Lower Third settings.
>
> **ChurchPresenter-PresentationEngine** — PPTX/PPT/Keynote/PDF parsing, timing and animation.
>
> **ChurchPresenter-Cross** — the crossword puzzle authoring tool. Not compiled into the app; a
> build-time task copies its encoded puzzles into the app's resources.


---

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:

- on macOS/Linux
```shell
  ./gradlew :composeApp:run
```
- on Windows
```shell
  .\gradlew.bat :composeApp:run
```

#### Forcing the dev fallback window

On a single-monitor machine with no DeckLink device, dev builds automatically open an extra
small windowed presenter output (since there's no second display to show it on). To get that
same window in a packaged/release build too — e.g. to demo or test presenter output without a
second monitor — set an environment variable before launching the app:

```shell
CHURCHPRESENTER_FORCE_DEV_WINDOW=true ./ChurchPresenter   # macOS/Linux
```
```shell
set CHURCHPRESENTER_FORCE_DEV_WINDOW=true && ChurchPresenter.exe   # Windows
```

Equivalently, the JVM system property `-Dchurchpresenter.forceDevWindow=true` works too (e.g.
via `JAVA_TOOL_OPTIONS`). This only affects whether the fallback window appears — it does not
change how the app reports itself for update checks, crash reporting, or usage analytics.

---

## 📚 Documentation

**For developers working on this project:**
- 📘 **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)** - Complete coding standards, style rules, and workflow
- 📋 **[DOCS_README.md](DOCS_README.md)** - Quick reference guide

**Build & deployment guides:**
- 🔨 **[BUILD_INSTALLERS.md](BUILD_INSTALLERS.md)** - How to build installers
- ⚡ **[QUICK_START_INSTALLERS.md](QUICK_START_INSTALLERS.md)** - Quick start guide
- 💻 **[MEMORY_CONFIGURATION.md](MEMORY_CONFIGURATION.md)** - Memory settings

**Before committing code:**
```bash
./cleanup_check.sh  # Run code quality checks
```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
