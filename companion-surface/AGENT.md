# `:companion-surface` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Companion surface** — a Bitfocus Companion button page mirrored inside the app:
- `CompanionSatelliteViewModel`, which owns one `CompanionSatelliteClient` per placement of each
  configured connection and keeps every surface's live button grid and connection state;
- `CompanionSurfacePanel`, one surface's grid and status, drawn in the tab and in the sidebars;
- `CompanionSurfaceTab`, with `CompanionConnectionChipRow` to choose between surfaces;
- `CompanionButtonState` and `CompanionConnectionUiState`, what the panel draws.

A real Gradle module of this build: `include(":companion-surface")`,
`implementation(projects.companionSurface)`. `:composeApp` is its only consumer: the tab area and
the two sidebars draw the panel, `CompanionSatelliteWiring` reconciles connections with settings,
and the Settings tab connects and disconnects through the view model.

It is the Compose face of `:companion-satellite`, which it takes as an `api` dependency; that module
stays free of any UI toolkit by design, so the bitmap decoding lives here. It also takes
`:shared-ui`, `:strings`, `:core-models`, `:settings`, `:theme` and `:diagnostics`, and nothing of
`:composeApp`'s.

## Layout

- **`CompanionSatelliteViewModel`** takes no `AppSettings`: every call is handed the current
  `CompanionSatelliteSettings`, so an edit applies without a restart. Slots are keyed by
  `CompanionSurfaceSlot` (a connection and a placement); each placement is its own device to
  Companion.
- **`CompanionSurfacePanel`** reads the slot's buttons and state straight from the view model. It
  is a rendering bridge to the view model and is passed it, as the tab is.

## Package

**`org.churchpresenter.companionsurface`**, with the screenshot suite in `.screenshot`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **No test opens a socket.** Every suite stubs `CompanionSatelliteClient`'s constructor with
  `mockkConstructor`; without it, selecting a surface connects to the configured host and the suite
  hangs rather than failing.
- Screenshots go through `stackedThemes` and are committed under `screenshots/` — see the root
  `AGENT.md` before re-recording them.

## Commands

```bash
./gradlew :companion-surface:test :companion-surface:detekt
./gradlew :companion-surface:jacocoTestCoverageVerification
./gradlew :companion-surface:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :companion-surface:verifyRoborazziJvm --tests '*ScreenshotTest*'
```

All of these run in CI, gated on this directory or a module it consumes changing.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**.
- **Coverage**: the root build's default six counters at 85% — **no** `coverageFloors`, **no**
  `coverageExcludes`.
