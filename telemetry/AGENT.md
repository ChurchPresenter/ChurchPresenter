# `:telemetry` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

What the app reports about itself, and nothing that draws:
- `LiveMapReporter`: the launch ping to the live map, with the anonymous setup facts and the
  usage-event counts it carries, and its retry;
- `UsageDetection.kt` and `UsageEventMappings.kt`: which presentations count as which usage event
  (dual-language songs, chord charts, split screens, multi-translation Bibles) and the mappings
  from the Calendar, Song Library and converter usage enums;
- `ContactReporter`: the in-app contact form's request and its outcomes;
- `DeviceInfoReport`: the diagnostic report the About box saves, with secrets left out;
- `GpuInfo`: the display adapters (Windows `EnumDisplayDevices`) for that report and the crash tags;
- `TelemetryIdentity`: the build these describe, in place of the app's `BuildConfig`.

A real Gradle module of this build: `include(":telemetry")`, `implementation(projects.telemetry)`.
`:composeApp` is its only consumer: `main.kt` pings and tags the crash reporter, the Songs and Bible
tabs and the tool windows record usage events, `ContactUsDialog` submits, and `AboutDialog` saves the
device report.

It reads the feature modules whose devices and usage it counts (`:canvas`, `:media`, `:web`,
`:live-output`, `:ndi`, `:omt`, `:calendar`, `:converter`, `:songlibrary`, `:song-chords`) and takes
nothing of `:composeApp`'s. It has no Compose.

## Seams to the app

- **`TelemetryIdentity`** — passed to `pingOnOpen`, `DeviceInfoReport.generate`, and as its
  version strings to `ContactReporter.defaultContext`/`submit`. The app builds it once from
  `BuildConfig` (`appTelemetryIdentity`).
- **`send`** on `pingOnOpen` and **`endpoint`** on `ContactReporter.submit` — the network step,
  defaulted to the real one; a test catches the ping or points the form at a local host.

## Package

**`org.churchpresenter.telemetry`**.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **No test reaches churchpresenter.org.** Pings go through `send` or a JDK `HttpServer` on port 0.
- The suite runs on JUnit 5: a temp folder is `@TempDir`, never JUnit 4's `TemporaryFolder`.

## Commands

```bash
./gradlew :telemetry:test :telemetry:detekt
./gradlew :telemetry:jacocoTestCoverageVerification
```

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**.
- **Coverage**: the root build's default six counters at 85% — no `coverageFloors`, no
  `coverageExcludes`. `GpuInfo.enumerate`'s Windows call is the one step left uncovered on purpose.
