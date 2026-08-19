# `:songlibrary` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Song Library Manager**: every song in the library folder in one editable grid, opened from the
Help menu beside the converter. A real Gradle module of this build — `include(":songlibrary")`,
`implementation(projects.songlibrary)`.

It **owns no model**. The song, the `.song` format and the library that loads a folder of them are
`:core-models`' (`core.models.songs`), so what this window writes is what the app reads on its next
scan. It takes `:core-models` and `:theme` and nothing else of the app's.

## Rules

- **It has no palette of its own.** `ui/Theme.kt` holds the window's metrics and *roles*, resolved
  from `MaterialTheme.colorScheme` and `:theme`'s `MaterialTheme.semantic`; the recessive chrome the
  dense table wants is alpha over that scheme, never a darker literal. A color literal belongs in
  `:theme` or nowhere — and this window opens inside the app's `AppThemeWrapper`, including
  standalone (`Main.kt` wraps it), so it follows all nine themes.
- **`SongLibraryState` sits outside `ui/` on purpose.** It is what detekt analyses and what JaCoCo
  measures, and `songlibrary/ui/**` is excluded from both as Compose desktop needing a display — an
  exclusion that is only honest while nothing with decisions in it is under `ui/`.
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
`coverageFloors`. detekt runs against the app's shared config with no baseline.
