# `:songlibrary` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Song Library Manager**: every song in the library folder in one editable grid, opened from the
Help menu beside the converter. A real Gradle module of this build — `include(":songlibrary")`,
`implementation(projects.songlibrary)`.

It **owns no model**. The song, the `.song` format and the library that loads a folder of them are
`:core-models`' (`org.churchpresenter.core.models.songs`), so what this window writes is what the app reads on its next
scan. It takes `:core-models`, `:song-chords` (the section-header and slide-break rules the
translation comparison splits lyrics by) and `:theme`, and nothing else of the app's.

## Package

**`org.churchpresenter.songlibrary`** (`.ui` for the screens). It was a bare top-level
`songlibrary`, with the Compose Resources class generated into `songlibrary.generated.resources`
to match.

That generated package is the part worth knowing about: it is set by `packageOfResClass` in
`build.gradle.kts`, **not** by any source file, and 70 imports depend on the two agreeing. Move one
without the other and the module stops compiling with unresolved `Res` references that no source
file explains.

## Rules

- **It has no palette of its own.** `LibraryMetrics` and `LibraryType` (`ui/Controls.kt`) hold the window's metrics and type; its colours are roles resolved
  from `MaterialTheme.colorScheme` and `:theme`'s `MaterialTheme.semantic`; the recessive chrome the
  dense table wants is alpha over that scheme, never a darker literal. A color literal belongs in
  `:theme` or nowhere — and this window opens inside the app's `AppThemeWrapper`, including
  standalone (`Main.kt` wraps it), so it follows all nine themes.
- **`SongLibraryState` sits outside `ui/` on purpose.** It holds the window's decisions, so they are
  tested as plain state; `ui/` is measured and analysed too, and its tests drive the window and
  assert what each control does.
- The logic the window runs on — filtering, sorting, pending edits, moving files — is in
  `:core-models` and tested there, so the window itself stays thin.

## Commands

```bash
./gradlew :songlibrary:run     # opens on ~/ChurchPresenter/Songs, or on a folder given as arg 1
./gradlew :songlibrary:test
./gradlew :songlibrary:detekt
./gradlew :songlibrary:jacocoTestCoverageVerification
```

All three gates run in CI, gated on this directory (or the shared build files) changing.

## Gates

The root build's six counters at 85%, **no floor lowered** — this module declares no
`coverageFloors`. detekt runs against the app's shared config, over `src/main/kotlin` and
`src/test/kotlin`, with no baseline.

**Coverage passes on all six counters, with nothing excluded.** Complexity has the least room —
about 85% — so a change that adds branches to `ui/` needs tests that take them. What stays
uncovered, and why: the generated resource accessors, the standalone `main()`, and
`CompareTranslationsDialog`'s `DialogWindow` (a real window, which cannot open headless —
`CompareTranslationsContent` inside it is tested on its own). Do not put an exclude in to make
room; see **NEVER exclude code from coverage without asking first** in the root `AGENT.md`.
