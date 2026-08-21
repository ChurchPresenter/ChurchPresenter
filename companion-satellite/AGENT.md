# `:companion-satellite` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`; the protocol write-up is in this directory's `README.md`.

## What it is

A pure-Kotlin client for **Bitfocus Companion's Satellite protocol** — plain TCP, default port
16622, line-based `COMMAND key=value key2="quoted value"` framing. It registers a virtual surface
with Companion, receives the button bitmaps Companion streams for it, and forwards presses back,
so a Stream Deck page can be mirrored inside ChurchPresenter.

A real Gradle module of this build: `include(":companion-satellite")`, consumed as
`implementation(projects.companionSatellite)`. It was the first module promoted out of the mounted
sub-builds, because it was the smallest — there is only one build to satisfy here, which is the
point.

## What `:composeApp` uses from it

`org.churchpresenter.companionsatellite.CompanionSatelliteClient` and its
`CompanionConnectionStatus`,
from `viewmodel/CompanionSatelliteViewModel.kt`, `composables/CompanionSurfacePanel.kt`,
`models/CompanionConnectionUiState.kt` and `dialogs/tabs/CompanionSatelliteSettingsTab.kt`.

## Layout

**The package is `org.churchpresenter.companionsatellite`.** It was a bare top-level
`companionsatellite`, with the Gradle `group` to match — the one root in this build with no
reverse-domain prefix at all, which is the shape most likely to collide with a dependency's own
package. Both now read `org.churchpresenter`.


The whole module is one file plus its test:

- `src/main/kotlin/org/churchpresenter/companionsatellite/CompanionSatelliteClient.kt` — `CompanionConnectionStatus`,
  `CompanionButtonUpdate` (raw RGB bytes, text, colors, pressed flag, page) and the client itself.
- `src/test/kotlin/org/churchpresenter/companionsatellite/FakeCompanion.kt` — an in-process fake Companion server the
  suite drives the client against. Prefer extending it over mocking the socket.

## Rules

- **No UI-toolkit dependency, by design.** The client hands back raw RGB bytes through
  `onButtonUpdated` so any consumer — Compose, a CLI, a test — decodes them however it likes.
  Never import Compose, AWT or an image type here; decoding belongs in `:composeApp`.
- **Registration uses the `LAYOUT_MANIFEST` `ADD-DEVICE` form**, where each button declares its own
  `(row, column)` on Companion's real page grid. The legacy `KEYS_TOTAL`/`KEYS_PER_ROW` form always
  anchors at row 0/column 0 and cannot offset into a page, which is what showing an arbitrary
  sub-rectangle of a larger page needs. Do not "simplify" back to it.
- Timing constants (ping interval, read timeout) are pinned to Companion's own 5s idle timeout and
  documented at the declaration — change them only against the protocol, not to fix a test.
- The protocol behavior is confirmed against `bitfocus/companion-satellite` and
  `bitfocus/companion` source; cite that way when adding a command.

## Commands

```bash
./gradlew :companion-satellite:test
./gradlew :companion-satellite:jacocoTestCoverageVerification
```

Both run in CI, gated on this directory or the shared build files changing.

## Gates

Coverage uses the root build's six counters (see the root `AGENT.md`). `extra["coverageFloors"]`
lowers BRANCH to 0.75 and COMPLEXITY to 0.70 — a socket client is mostly branch-dense framing and
error handling — and the other four counters stay at the 85% default. There are no
`coverageExcludes`: everything here is testable against `FakeCompanion`.

## Dependencies

`kotlinx-coroutines-core` only (plus `kotlinx-coroutines-test` for the suite), from
`gradle/libs.versions.toml`. Keep it that way — the module's value is that it is plain Kotlin and
sockets.
