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
> **[`dictionary/`](./dictionary)** — the bundled study data behind the Dictionary tab: the
> Strong's dictionary in English and Russian, and the interlinear index that says where each of its
> numbers occurs in scripture. Six JSON files and the lookups over them, and nothing else — no
> Compose, no Ktor. `./gradlew :dictionary:test`.
>
> **[`dictionary-tab/`](./dictionary-tab)** — the Dictionary tab itself: the Strong's browser with
> its search, language filter and history, the "In Scripture" panel beside it, and the presenter
> that puts an entry on the screen. The first tab to live outside `:composeApp`, which keeps only
> the wiring. `./gradlew :dictionary-tab:test`.
>
> **[`qa-tab/`](./qa-tab)** — the Audience Q&A tab: the moderation queue people post to from their
> phones, the QR code they scan to find it, the sharing dialog that works out the two addresses, and
> the presenter that puts a question on the screen. It drives the outputs through its own `QaOutput`
> port, so `PresenterManager` stays in the app; its `QAManager` is what `:companion-server` moderates
> through. `./gradlew :qa-tab:test`.
>
> **[`stt-tab/`](./stt-tab)** — the Live Captions tab: the connection to a speech-to-text
> server, the transcript and translation it streams, and the presenter that draws them on the
> screen. It reaches the outputs through its own `SttOutput` port, so `PresenterManager` stays
> in the app; its `STTManager` is what the Bible tab's auto-follow, the presenter windows and
> the Browser Source overlay all read the live transcript from. The settings dialog stays in
> the app with the other options pages. `./gradlew :stt-tab:test`.
> **[`web-tab/`](./web-tab)** — the Web tab: the embedded Chromium browser the operator drives,
> its bookmarks, zoom and device emulation, and the presenter that puts a live page on the
> screen. It reaches the outputs through its own `WebOutput` port — a wider one than the other
> tabs', because when a page is live the tab and the output share the *same* browser, so the
> reference itself crosses the boundary. `./gradlew :web-tab:test`.
>
> **[`lower-third-tab/`](./lower-third-tab)** — the Lower Third tab: the animated Lottie band the
> audience sees over the picture, its presets and playback, the offscreen renderer the Browser Source
> overlay and the ATEM upload both rasterise through, and the media-pool upload itself. It was
> already callback-driven before it moved, so it needs no port — `PresenterManager` never reached it.
> `./gradlew :lower-third-tab:test`.
>
> **[`dictionary-settings-tab/`](./dictionary-settings-tab)** — the page of the options dialog that
> styles that output: the colour, font, size, style and shadow of the word, its definition, its
> reference and its KJV usage, plus the card background and the fades. Depends on the settings
> fields rather than on the dictionary. `./gradlew :dictionary-settings-tab:test`.
>
> **[`lower-third-settings-tab/`](./lower-third-settings-tab)** — the page of the options dialog
> that configures the Lower Third tab: the Lottie library on the left, a live preview of the
> selected animation on the right, and the window insets the band is placed with beneath it.
> Unlike the dictionary pair it does depend on its tab module — the preview is the same
> compottie render, with the same bundled fonts, as the thing the audience sees.
> `./gradlew :lower-third-settings-tab:test`.
>
> **[`announcements-tab/`](./announcements-tab)** — the Announcements tab: on-screen notices and the
> four timers beside them — a duration, a count-up, a countdown to a time of day and a live clock —
> with the presenter that draws whichever is live. It reaches the output through its own
> `AnnouncementsOutput` port, so `PresenterManager` stays in the app.
> `./gradlew :announcements-tab:test`.
>
> **[`companion-server/`](./companion-server)** — the HTTP/WebSocket surface the desktop exposes:
> the wire format, the routes, the pages it serves a browser, TLS and the tunnel, plus the client a
> follower instance consumes the same surface with. One server behind the phone companion app, the
> Q&A page, the OBS Browser Source overlay, the presentation remote and Instance Link. What a remote
> request then *does* to the app stays in `:composeApp`, under `remote/`.
> `./gradlew :companion-server:test`.

> **[`statistics/`](./statistics)** — what was sung and read, counted: the all-time tallies, the
> timestamped play log behind them, and the CSV/Excel exports a CCLI licence report is filed
> from. The window that draws it stays in the app; this is the numbers.
> `./gradlew :statistics:test`.
>
> **[`resources/`](./resources)** — every asset the app draws or reads: the icons, the interface
> strings in all 35 languages, the fonts, and the bundled sample songs, bibles, backgrounds and
> licences. One `Res` accessor for the lot, so a call site reads `Res.drawable.ic_close` and
> `Res.string.save` just as it always has. Assets only — no Kotlin, no suite.
>
> **[`song-chords/`](./song-chords)** — the grammar songs are written in: what counts as a chord,
> what counts as a section heading, transposition, and turning a pasted chord sheet into the inline
> `[G]lyric` markup. Depends on nothing, so the app and the converter share one rule instead of two.
> `./gradlew :song-chords:test`.
>
> **[`shortcuts/`](./shortcuts)** — the keyboard shortcuts: every action the app can be driven
> by, what each is currently bound to once the operator's overrides are applied, and how a
> binding is written out — as a label, as individual keycaps, and as the text the shortcuts
> search matches. Its own module because every tab reads it. `./gradlew :shortcuts:test`.
>
> **[`songlibrary/`](./songlibrary)** — the Song Library Manager: every song in the library folder in one editable grid, opened from the Help menu. It reads and writes through **[`core-models/`](./core-models)**, which holds the song model and the `.song` file format the app itself uses.
>
> **[`converter/`](./converter)** — a song/bible format converter built with Compose Desktop,
> accessible from the Help menu. `./gradlew :converter:test`, `./gradlew :converter:packageDmg`.
>
> **[`companion-satellite/`](./companion-satellite)** — a pure-Kotlin Bitfocus Companion Satellite
> protocol client. `./gradlew :companion-satellite:test`.
>
> **[`ndi/`](./ndi)** — NDI output: sending this app's live content over the network as an NDI®
> source, so OBS, vMix or a hardware switcher can take lyrics or a lower third straight off the LAN
> with no capture card. Alpha mode carries genuine per-pixel transparency, so a lower third arrives
> already keyed — something SDI physically cannot do. **Ships no NDI binaries**: the free NDI
> Runtime is installed separately and auto-detected, exactly as VLC is. `./gradlew :ndi:test`.
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
