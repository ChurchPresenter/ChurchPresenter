# `:ndi` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The send half of NDI** — Vizrt's Network Device Interface — as a plain Kotlin library: find the
runtime, bring it up, create a named source, push frames at it, count who is watching, tear it down.
A real Gradle module of this build: `include(":ndi")`, `implementation(projects.ndi)`.

The package is `org.churchpresenter.ndi`.

**Receive is deliberately not here.** An NDI *receiver* is a second live media pipeline with its own
reconnect, dropped-frame and audio-sync behaviour; if it is ever wanted it belongs as a `SceneSource`
in the Canvas compositor beside the camera and screen-capture sources, not bolted onto this.

## **This module ships no NDI binaries, and never may**

Vizrt's licence does not permit redistributing the NDI Runtime. That is not a packaging
inconvenience to work around — it is the constraint the whole design follows from, and it is the
same wall `obs-ndi`/DistroAV hit and resolved the same way: load `libndi` at run time, and tell the
user where to get it when it is not there.

So NDI behaves here **exactly as VLC already does**: a separately installed runtime, auto-detected
at startup, overridable by a path in settings, and the feature reading as *not installed yet* rather
than as a failure when it is absent. `NdiRuntimeStatus` has four cases for that reason — only two of
them are faults.

Do not add a `libndi` to `resources/`, to `appResources/`, or to a packaging task. Do not vendor it
into a fat jar. If someone asks for "one-click NDI", the answer is a link to the runtime download.

### Two licence terms that are not optional
- **`NDI®` attribution in the About box**, and a link to ndi.video wherever NDI is selected. Both
  are implemented (`AboutDialog.kt`, `ProjectionNdiCard.kt`, the `ndi_trademark` string) and both
  are required by the terms, not by taste. Removing either is a licence violation, not a cleanup.
- The trademark line is **not** conditional on a runtime being installed — the app offers the
  feature either way.

## Layout

| File | Owns |
|---|---|
| `NdiRuntime.kt` | Where the runtime is: the env vars, the platform defaults, the search. Pure over its arguments — the environment and the filesystem are passed in |
| `NdiRuntimeStatus.kt` | The four outcomes of looking, and `NdiRuntimeHost` — one runtime per process, handing out senders |
| `NdiLibrary.kt` | The six native calls, as an interface, plus `NdiVideoFrame`. **The seam** |
| `JnaNdiLibrary.kt` | The only file that knows JNA exists: the C symbols, the two ABI structs, the native pixel buffer |
| `NdiSender.kt` | One source on the network — the fill, and the key beside it when the mode wants one |
| `NdiPixelFormat.kt` | The FourCC codes and `NdiOutputMode` |
| `NdiPixels.kt` | ARGB → NDI byte order, into a buffer the caller reuses |

App-side wiring is **not** here, the way `AtemBridge` is not in `:atem`: `NdiVideoRenderer`,
`NdiManager` and `ProjectionNdiCard` live in `:composeApp`.

## Rules

- **Nothing here may read a setting, touch a ViewModel, or import Compose.** The stored `ndiMode`
  strings are `:settings`' `Constants.NDI_MODE_*`; turning one into an `NdiOutputMode` is
  `NdiVideoRenderer`'s job, in the app.
- **Every native call goes through `NdiLibrary`.** That is what lets the suite drive the whole
  module against `FakeNdiLibrary` with no runtime installed, and it is why the JNA marshalling
  itself is covered too — `NdiLibC` is an interface, so `JnaNdiLibrary` is tested against a
  stand-in for the C symbols. **`JnaNdiLibrary.load` is the one genuinely uncovered call.**
- **`@Structure.FieldOrder` and the field names are the ABI.** Reordering or renaming a field in
  `NdiSendCreateStruct`/`NdiVideoFrameStruct` silently sends garbage — no compile error, no
  exception, a wrong picture. Both carry `@Suppress("VariableNaming")` for that reason; so does
  `NdiLibC` for `FunctionNaming`. Those suppressions are load-bearing.
- **Buffers are reused, never reallocated per frame.** At 1080p a fresh array per frame is 8.3 MB
  of garbage 30 times a second — the exact allocation profile the Browser Source renderer was
  explicitly fixed to stop paying. `NdiSender` and `JnaNdiLibrary` each grow one buffer and keep it.
- **An `NdiSender` is driven by exactly one pump.** That is what makes the reused buffers safe; two
  threads through one sender tears a frame.

## Why JNA

- **Devolay** (`me.walkerknapp:devolay`) is the only published Java NDI binding and is effectively
  unmaintained — last commit July 2024, Apple Silicon PR closed unmerged. It hardcodes the
  `libndi.so.5` soname that NDI 6 no longer ships on Linux, which is precisely what
  `libraryFileNamesFor` tries `.so.6` first to avoid.
- **Panama/FFM** is the modern answer and is finalized in JDK 22. This build targets 21.
- **JNA** works on 21 and on arm64, was already a dependency of this app via vlcj, and the send API
  is six functions. No C++ shim, no prebuilt natives to build and commit for three platforms.

The version is `jna` in `gradle/libs.versions.toml` — `:composeApp` uses the same alias. Never a
hand-copied literal.

## Commands

```bash
./gradlew :ndi:test
./gradlew :ndi:detekt                            # gate — no baseline, must be clean
./gradlew :ndi:jacocoTestCoverageVerification
```

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**, main and test in scope (not
  `testFixtures`, as `:atem` does it). The two ABI suppressions above are at the declaration.
- **Coverage**: the root build's default six counters at 85%, all of them — **no**
  `coverageFloors`, **no** `coverageExcludes`, and it should stay that way. Covering the JNA
  marshalling against a fake `NdiLibC` is what earns that; an exclude for "it needs a runtime"
  would have hidden a real bug — `Memory(0)` throws, so a zero-sized frame took the render loop
  down until a test found it.

## Testing live sending

Sending for real needs the runtime *and* a receiver, so it cannot be a unit test. Do it by hand:
install the runtime, add an output in Projection settings, and pick the source in OBS or NDI Studio
Monitor. `FakeNdiLibrary` (a test fixture, so `:composeApp` shares it) covers everything else.

## Dependencies

`jna` and `:diagnostics` (CrashReporter, at the one load site). Nothing else, ever — no Compose, no
Ktor, no settings.
