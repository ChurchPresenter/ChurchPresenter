# `:crossword` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`; the puzzle-authoring workflow and the plaintext clue format are in this directory's
`README.md`.

## What it is

The **crossword puzzle authoring tool** — a standalone Compose Desktop editor plus the encoded
puzzles the app ships. An operator writes clues, watches the grid build live, and exports
`encoded/levelN.xwp`; the filename decides the level order.

A real Gradle module of this build: `include(":crossword")`. It is the one module `:composeApp`
does **not** depend on — there is no `implementation(projects.crossword)`. The only link is a
build-time copy:

```
crossword/encoded/*.xwp  ──[ syncCrosswordFiles ]──►  composeApp/src/jvmMain/composeResources/files/crossword/
```

`syncCrosswordFiles` is declared in `composeApp/build.gradle.kts` and every `*ProcessResources*`
task depends on it. So a new puzzle reaches the app by being exported into `encoded/` and
committed; nothing else is needed.

## The one cross-module rule

The app decodes puzzles in `data/CrosswordData.kt` (`CrosswordDecoder`), which **mirrors this
module's `data/Encoder.kt` and shares its XOR key** (`CHURCHPRESENTER`, Base64 over XOR). The two
files have no compile-time relationship — nothing will fail to build if they drift, and the
symptom is puzzles that decode to garbage in the app only. **Change one and change the other**, and
re-run the app's `CrosswordDataTest` as well as this module's `EncoderTest`.

The app side also carries its own model types (`CrosswordClue`, `CrosswordCell`,
`RenderedCrossword`) parallel to this module's `ClueEntry`/`GridCell`/`RenderedPuzzle`. Same rule:
they are duplicated deliberately, because the app must not depend on this module.

In the app the crossword is a hidden tab, unlocked by a key sequence in `MainDesktop.kt` and drawn
by `tabs/CrosswordTab.kt`.

## Layout

`src/main/kotlin/org/churchpresenter/cross/`

| File | Owns |
|---|---|
| `Main.kt` | `mainClass = "org.churchpresenter.cross.MainKt"` — the editor window |
| `data/Models.kt` | `Direction`, `ClueEntry`, `GridCell`, `RenderedPuzzle` |
| `data/CrosswordEngine.kt` | `build(clues)` — places answers on a grid, longest first, every later word intersecting an earlier one; returns null when the clues cannot form a valid crossword |
| `data/Encoder.kt` | `encode`/`decode` (Base64 + XOR), `toPlaintext`, `fromPlaintext`, `fromPlaintextSimple` |
| `ui/` | `AdminApp` (the editor), `CrosswordPreview` (the live grid), theme and `Strings` |

`encoded/` holds the committed `.xwp` puzzles; `puzzles/` holds plaintext sources and is
**gitignored except `level0.txt`** (`.gitignore` in this directory) — an author's working files
stay on their machine.

Tests assert **invariants of a valid crossword** — connectivity, intersections, numbering in
reading order — not a fixed expected grid, so the placement algorithm can change without rewriting
the suite. Keep new tests in that style.

## Commands

```bash
./gradlew :crossword:test                              # its suite (headless)
./gradlew :crossword:run                               # the editor
./gradlew :crossword:jacocoTestCoverageVerification    # the coverage floor
./gradlew :crossword:packageDmg                        # installer (Msi/Deb also available)
./gradlew :composeApp:syncCrosswordFiles               # copy encoded/*.xwp into app resources
```

Both CI steps are gated on this directory or the shared build files changing.

## Gates

- **Coverage**: the root build's default six counters at 85%, all six — this module declares **no**
  `coverageFloors` and clears them comfortably. `extra["coverageExcludes"]` drops `**/ui/**` and
  `**/MainKt*`: the editor is Compose Desktop and needs a display. The data layer is the part that
  is measured, and it is the part that matters.
- There is no detekt task on this module.
- Tests run with `java.awt.headless=true`.

## History worth knowing

It was Kotlin Multiplatform with a `jvm()` target only (`src/jvmMain`, `src/jvmTest`). As a module
of this build it is `kotlin("jvm")` with `src/main/kotlin` and `src/test/kotlin`, which is what puts
it under the root build's shared JaCoCo wiring and makes its suite `:crossword:test` rather than
`jvmTest`. Don't reintroduce the KMP layout.

## Dependencies

`compose.desktop.currentOs`, Material 3, the extended icon set, `kotlinx-coroutines-swing` — all
from `gradle/libs.versions.toml`. No serialization: the `.xwp` format is the hand-written encoder.
