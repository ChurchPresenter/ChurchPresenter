# `:ndi` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**NDI** — Vizrt's Network Device Interface — as a plain Kotlin library, both directions: find the
runtime, bring it up, create a named source, push frames at it, count who is watching, tear it down;
and, coming the other way, discover who else is sending and pull one of those sources back in as
packed ARGB. A real Gradle module of this build: `include(":ndi")`, `implementation(projects.ndi)`.

The package is `org.churchpresenter.ndi`.

**Receive is video only, and that is deliberate.** `recvCaptureVideo` asks the runtime for audio and
metadata and lets it drop both — a receiver that accepted audio nobody drained would grow the SDK's
own queue for the length of a service. If audio is ever wanted it is a second pipeline with its own
sync behaviour, not a flag on this one.

The app-side wiring for receive lives in `:composeApp` beside the camera and screen-capture sources
it sits next to in the Canvas: `NdiFrameCache` (one connection per source, reference counted) and
`NdiSourceDirectory` (one finder, held open while a picker is looking).

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
| `NdiLibrary.kt` | The fourteen native calls, as an interface, plus `NdiVideoFrame`. **The seam** |
| `JnaNdiLibrary.kt` | The only file that knows JNA exists: the C symbols, the five ABI structs, the native pixel buffers |
| `NdiSender.kt` | One source on the network — the fill, and the key beside it when the mode wants one |
| `NdiFinder.kt` | Discovery: one long-lived finder, and what it knows so far |
| `NdiReceiver.kt` | One source being received — the connection, the conversion and the reused buffer |
| `NdiSourceInfo.kt` | A source's name and address, the pair everything on the receive side is keyed by |
| `NdiPixelFormat.kt` | The FourCC codes, `NdiOutputMode`, and the FourCC → format lookup a received frame needs |
| `NdiPixels.kt` | ARGB ⇄ NDI byte order, into a buffer the caller reuses |

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
- **An `NdiSender` is driven by exactly one pump, and so is an `NdiReceiver`.** That is what makes
  the reused buffers safe; two threads through one sender tears a frame, and two through one
  receiver read a buffer while it is being overwritten. `JnaNdiLibrary` keeps one buffer **per
  handle** for the same reason, on both sides.
- **A received frame's pixels are borrowed, not given.** `NdiLibrary.recvCaptureVideo` and
  `NdiReceiver.receive` both hand back a buffer that the next call overwrites — copy what you need
  before asking for another frame. `NdiFrameCache` does that into a `BufferedImage` immediately.
- **`line_stride_in_bytes` is not `width * 4`.** The runtime is entitled to pad rows, and a copy
  that assumes packed rows shears the picture. `copyReceivedFrame` reads row by row unless the frame
  happens to be packed.
- **Every captured frame must be freed, including one that is dropped.** A format we do not read is
  still the runtime's memory until `NDIlib_recv_free_video_v2` returns it — hence the `finally`.

## Why JNA

- **Devolay** (`me.walkerknapp:devolay`) is the only published Java NDI binding and is effectively
  unmaintained — last commit July 2024, Apple Silicon PR closed unmerged. It hardcodes the
  `libndi.so.5` soname that NDI 6 no longer ships on Linux, which is precisely what
  `libraryFileNamesFor` tries `.so.6` first to avoid.
- **Panama/FFM** is the modern answer and is finalized in JDK 22. This build targets 21.
- **JNA** works on 21 and on arm64, was already a dependency of this app via vlcj, and the API this
  app needs is sixteen functions. No C++ shim, no prebuilt natives to build and commit for three
  platforms.

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

## Testing against a real runtime

`NdiHardwareTest` binds the actual NDI Runtime instead of `FakeNdiLibrary`. It is **opt-in and inert
by default**, on the `DeckLinkHardwareTest` model:

```bash
./gradlew :ndi:test -PndiHardware=true --tests '*NdiHardwareTest*'
```

Gated rather than self-skipping because it loads a 30 MB native library, starts the runtime's own
threads, and **advertises a source every NDI receiver on the LAN can discover** — on a machine
mid-service, a stray source in the operator's list.

It exists because the fake proves the logic but cannot prove the *binding*: that `libndi` exports the
flat C symbols `NdiLibC` declares, and that `NdiSendCreateStruct`/`NdiVideoFrameStruct` match the
SDK's ABI field-for-field. Those fail **silently** — a wrong `@Structure.FieldOrder` is not a compile
error and not an exception, it is a wrong picture.

**Receive verified 2026-08-30 against the same SDK, as a full loopback**: a sender put up, found by
discovery as `AIS-MAC-MINI.LOCAL (ChurchPresenter Loopback Test)` at `127.0.0.1:5961`, connected to,
and a 16x16 frame read back with its red channel intact. That is the only thing that can check
`NdiFindCreateStruct`, `NdiSourceStruct` and `NdiRecvCreateStruct`, because a wrong field order
there is not a crash — the receiver simply connects to nothing and no frame ever arrives.

**A source's advertised name is `MACHINE (Name)`, not the name it was created with.** The loopback
test matches by containment for that reason, and the picker on the Canvas stores the full advertised
string, which is what a receiver connects by.

**Verified 2026-08-26 against NDI SDK 6.3.2.0 on macOS** (`NDI SDK APPLE ... 6.3.2.0`, found at
`/Library/NDI SDK for Apple/lib/macOS/libndi.dylib`): discovery, `JnaNdiLibrary.load`, the version
and CPU checks, and a frame sent in all three modes — so the flat-symbol assumption and both struct
layouts are confirmed, not assumed.

What `NdiHardwareTest` deliberately does **not** assert is that a receiver saw a *correct picture*.
That is `NdiLiveSenderProbe`, which holds a source up for a human to look at:

```bash
./gradlew :ndi:test -PndiHardware=true -PndiSeconds=240 --tests '*NdiLiveSenderProbe*'
```

**Its test pattern is load-bearing, and the obvious one is wrong.** An opaque shape on a transparent
field cannot answer the question: alpha=0 over RGB=black looks black whether the alpha arrived or
was dropped. The probe instead holds RGB at pure white and varies *only* the alpha byte across four
bands (255/170/85/0), so the outcomes are visually opposite — a brightness staircase if alpha
survives, a flat white rectangle if it did not.

**Verified 2026-08-26, NDI SDK 6.3.2.0, received in NDI Video Monitor: a staircase darkening toward
the bottom.** Alpha survives the BGRA path; a lower third reaches OBS already keyed, which is the
whole premise of this feature.

## Dependencies

`jna` and `:diagnostics` (CrashReporter, at the one load site). Nothing else, ever — no Compose, no
Ktor, no settings.
