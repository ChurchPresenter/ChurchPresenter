ChurchPresenter is a Compose Multiplatform desktop app (JVM only) for Windows, macOS and Linux.

* [/composeApp](./composeApp/src) is the app itself: all of its code is in
  [jvmMain](./composeApp/src/jvmMain/kotlin) and its tests in [jvmTest](./composeApp/src/jvmTest/kotlin).
* Every other top-level directory with a `build.gradle.kts` is a module of the same build, listed
  below.

---

## 🚀 Getting Started

Clone the repository — there are no submodules, so a plain clone is everything you need:
```shell
git clone https://github.com/ChurchPresenter/ChurchPresenter
```

The app is built from several modules that all live in this repository — there are no submodules and
nothing to fetch separately. Every one of them is a **Gradle module of this build**, with no
wrapper of its own — one `./gradlew` at the repo root builds and tests the lot:

> **[`settings/`](./settings)** — everything the app persists: the settings data classes, the
> `SettingsManager` that loads, migrates and saves `settings.json`, and the constants their
> defaults are written with. `./gradlew :settings:test`.
>
> **[`diagnostics/`](./diagnostics)** — crash reporting: the crash log written to
> `~/.churchpresenter/crash-reports/` and the Sentry forwarding behind it, including the PII
> scrubbing every outgoing event passes through. `./gradlew :diagnostics:test`.
>
> **[`planning-center/`](./planning-center)** — the Planning Center Online client: the OAuth
> conversation, the Services API calls that read a service plan, and the loopback listener that
> catches the consent redirect. Each church brings its own free PCO Developer credentials; nothing
> is written back. `./gradlew :planning-center:test`.
>
> **[`bible-formats/`](./bible-formats)** — getting a Bible onto disk: the eBible, Zefania and
> Beblia catalogues the download browser lists, and the converters that turn USFX and Zefania XML
> into the `.spb` format the app reads. `./gradlew :bible-formats:test`.
>
> **[`bible/`](./bible)** — the Bible itself: a loaded `.spb` translation, its books, its two
> numberings and the search over them. Not to be confused with `:bible-engine` (speech-to-reference
> detection) or `:bible-formats` (the download catalogues and the converters that *produce* `.spb`).
> `./gradlew :bible:test`.
>
> **[`song-chords/`](./song-chords)** — the grammar songs are written in: what counts as a chord,
> what counts as a section heading, transposition, and turning a pasted chord sheet into the inline
> `[G]lyric` markup. Depends on nothing, so the app and the converter share one rule instead of two.
> `./gradlew :song-chords:test`.
>
> **[`calendar/`](./calendar)** — the Calendar Manager: every planned service on a month grid, each
> with a run of show that loads into the Schedule, opened from the Help menu. Pairs phones through an
> end-to-end encrypted relay. `./gradlew :calendar:test`.
>
> **[`songlibrary/`](./songlibrary)** — the Song Library Manager: every song in the library folder in one editable grid, opened from the Help menu. It reads and writes through **[`core-models/`](./core-models)**, which holds the song model and the `.song` file format the app itself uses.
>
> **[`converter/`](./converter)** — a song/bible format converter built with Compose Desktop,
> accessible from the Help menu. `./gradlew :converter:test`, `./gradlew :converter:packageDmg`.
>
> **[`companion-satellite/`](./companion-satellite)** — a pure-Kotlin Bitfocus Companion Satellite
> protocol client. `./gradlew :companion-satellite:test`.
>
> **[`ndi/`](./ndi)** — NDI itself, both directions: putting an output on the network as an NDI
> source, and finding and receiving someone else's to draw on the Canvas. The NDI Runtime is a
> separate free download, detected at startup exactly as VLC is. `./gradlew :ndi:test`.
>
> **[`omt/`](./omt)** — Open Media Transport, both directions: an output put on the network as an
> OMT source, and someone else's received onto the Canvas. Unlike NDI the libraries are MIT and ship
> inside the app, fetched and pinned at build time as ffmpeg is. `./gradlew :omt:test`.
>
> **[`atem/`](./atem)** — the Blackmagic ATEM protocol client: the UDP conversation with the
> switcher, from the handshake to a media-pool upload. Its suite runs against a loopback fake
> switcher built from a capture of real hardware, so no device is needed.
> `./gradlew :atem:test`.
>
> **[`theme/`](./theme)** — the app's look: the nine color schemes, the semantic color roles, the
> typography and shape scales. `./gradlew :theme:test`.
>
> **[`core-models/`](./core-models)** — the shared data models (schedule items, scenes, questions,
> lyrics). `./gradlew :core-models:test`.
>
> **[`bible-engine/`](./bible-engine)** — the Bible Lookup Engine: speech-to-reference detection.
> `./gradlew :bible-engine:test`.
>
> **[`lottieGenerator/`](./lottieGenerator)** — a standalone Compose Desktop app for generating
> animated lower-third overlays as Lottie JSON files, launched from the Lower Third settings.
> `./gradlew :lottieGenerator:test`.
>
> **[`crossword/`](./crossword)** — the crossword puzzle authoring tool. Not compiled into the app;
> a build-time task copies its encoded puzzles into the app's resources.
> `./gradlew :crossword:test`, `./gradlew :crossword:run`.
>
> **[`presentation-engine/`](./presentation-engine)** — PPTX/PPT/Keynote/PDF parsing, timing and
> animation, entirely in-JVM. `./gradlew :presentation-engine:test`.


---

### The bundled ffmpeg

Cameras and capture cards are opened by running **ffmpeg**, and the app ships its own copy rather
than asking anyone to install one. It is **not committed** — `:composeApp:fetchBundledFfmpeg`
downloads the build pinned in `gradle/ffmpeg-builds.properties`, verifies its SHA-256 and writes the
single `ffmpeg` program into `composeApp/src/jvmMain/appResources/<os>/`, which is what packaging
and `run` both read. Packaging and `run` depend on that task, so there is nothing to do by hand.
On macOS, when a signing identity is configured, `:composeApp:signBundledFfmpeg` then codesigns it
with the hardened runtime and `desktop/macos/ffmpeg.entitlements` before it goes into the bundle —
Compose never signs anything under `appResources`, and notarization rejects the DMG otherwise.

A target with no URL configured is not an error: the app then falls back to whatever ffmpeg is
installed on the machine, and Settings → Projection → Camera Capture says which one it is using.
Licensing and the written offer of source are in [THIRD_PARTY_FFMPEG.md](THIRD_PARTY_FFMPEG.md).

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

**Running on a machine:**
- 🖥️ **[GRAPHICS_BACKEND.md](GRAPHICS_BACKEND.md)** - Choosing the GPU backend, and what it does to screen capture

**Before committing code:**
```bash
./cleanup_check.sh  # Run code quality checks
```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
