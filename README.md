# ChurchPresenter

Everything a church needs to put words on the screen — songs, scripture, slides, video, captions and
broadcast graphics — in one free, open-source desktop app for Windows, macOS and Linux.

It is a Compose Multiplatform app on the JVM. [FEATURES.md](FEATURES.md) lists every feature and
where its source lives.

---

## Getting started

### Requirements

A JDK 17 or newer to run Gradle. The build itself compiles against JDK 21, which Gradle downloads on
its own if the machine does not have one.

### Clone

There are no submodules, so a plain clone is everything you need:

```shell
git clone https://github.com/ChurchPresenter/ChurchPresenter
```

### Build and run

Use the run configuration in your IDE's toolbar, or run it from the terminal:

```shell
./gradlew :composeApp:run        # macOS/Linux
```
```shell
.\gradlew.bat :composeApp:run    # Windows
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

### Tests

```shell
./gradlew :composeApp:check    # compile and every unit test of the app
./gradlew :settings:test       # one module's own suite; the same for any module below
bash test-changed.sh           # only the suites your change touches
```

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

## Project layout

[`composeApp/`](./composeApp/src) is the app itself: all of its code is in
[`jvmMain`](./composeApp/src/jvmMain/kotlin) and its tests in [`jvmTest`](./composeApp/src/jvmTest/kotlin).

Every other top-level directory with a `build.gradle.kts` is a **Gradle module of this build**, with
no wrapper of its own: one `./gradlew` at the repository root builds and tests them all. Each module
documents itself in its own `AGENT.md`.

| Module | What it is |
|---|---|
| [`settings/`](./settings) | Everything the app persists: the settings data classes, the `SettingsManager` that loads, migrates and saves `settings.json`, and the constants their defaults are written with. |
| [`diagnostics/`](./diagnostics) | Crash reporting: the crash log written to `~/.churchpresenter/crash-reports/` and the Sentry forwarding behind it, including the PII scrubbing every outgoing event passes through. |
| [`core-models/`](./core-models) | The shared data models (schedule items, scenes, questions, lyrics), and the song model and `.song` file format. |
| [`theme/`](./theme) | The app's look: the nine color schemes, the semantic color roles, the typography and shape scales. |
| [`bible/`](./bible) | The Bible itself: a loaded `.spb` translation, its books, its two numberings and the search over them. |
| [`bible-formats/`](./bible-formats) | Getting a Bible onto disk: the eBible, Zefania and Beblia catalogues the download browser lists, and the converters that turn USFX and Zefania XML into `.spb`. |
| [`bible-engine/`](./bible-engine) | The Bible Lookup Engine: speech-to-reference detection. |
| [`song-chords/`](./song-chords) | The grammar songs are written in: chords, section headings, transposition, and turning a pasted chord sheet into inline `[G]lyric` markup. Depends on nothing, so the app and the converter share one rule. |
| [`songlibrary/`](./songlibrary) | The Song Library Manager: every song in the library folder in one editable grid, opened from the Help menu. |
| [`calendar/`](./calendar) | The Calendar Manager: every planned service on a month grid, each with a run of show that loads into the Schedule, opened from the Help menu. Pairs phones through an end-to-end encrypted relay. |
| [`presentation-engine/`](./presentation-engine) | PPTX/PPT/Keynote/PDF parsing, timing and animation, entirely in-JVM. |
| [`lottieGenerator/`](./lottieGenerator) | A standalone Compose Desktop app that generates animated lower thirds as Lottie JSON, also opened from the Lower Third settings. |
| [`converter/`](./converter) | A song/Bible format converter, also a standalone app, opened from the Help menu. `./gradlew :converter:packageDmg` packages it. |
| [`crossword/`](./crossword) | The crossword authoring tool. Not compiled into the app: a build-time task copies its encoded puzzles into the app's resources. `./gradlew :crossword:run` opens it. |
| [`planning-center/`](./planning-center) | The Planning Center Online client: the OAuth conversation, the Services API calls that read a service plan, and the loopback listener that catches the consent redirect. Each church brings its own free PCO Developer credentials; nothing is written back. |
| [`companion-satellite/`](./companion-satellite) | A pure-Kotlin Bitfocus Companion Satellite protocol client. |
| [`atem/`](./atem) | The Blackmagic ATEM protocol client: the UDP conversation with the switcher, from the handshake to a media-pool upload. Its suite runs against a loopback fake switcher built from a capture of real hardware, so no device is needed. |
| [`ndi/`](./ndi) | NDI in both directions: an output put on the network as an NDI source, and someone else's received onto the Canvas. The NDI Runtime is a separate free download, detected at startup as VLC is. |
| [`omt/`](./omt) | Open Media Transport in both directions. Unlike NDI the libraries are MIT and ship inside the app, fetched and pinned at build time as ffmpeg is. |

Every module's suite runs with `./gradlew :<module>:test`.

---

## Documentation

[DOCS_README.md](DOCS_README.md) indexes every document. The ones most people want:

- [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md): workflow, verification commands and contributing.
- [CODING_STANDARDS.md](CODING_STANDARDS.md): the style rules.
- [AGENT.md](AGENT.md): architecture, modules, commands, and the screenshot and test rules.
- [BUILD_INSTALLERS.md](BUILD_INSTALLERS.md) and [QUICK_START_INSTALLERS.md](QUICK_START_INSTALLERS.md): building the installers.
- [GRAPHICS_BACKEND.md](GRAPHICS_BACKEND.md): choosing the GPU backend on a machine, and what it does to screen capture.
- [COMPANION_API.md](COMPANION_API.md): the REST and WebSocket API the app exposes for the mobile companion.

### Before every commit

```bash
bash cleanup_check.sh          # wildcard imports, Material 2, prints, fully qualified names, unused code
./gradlew :composeApp:detekt   # CI's first gate; run it last
```

---

## License

GPL-3.0. See [LICENSE.txt](LICENSE.txt). The bundled ffmpeg and OMT libraries carry their own
terms: [THIRD_PARTY_FFMPEG.md](THIRD_PARTY_FFMPEG.md), [THIRD_PARTY_OMT.md](THIRD_PARTY_OMT.md).
