# `:crossword-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The hidden **Crossword** tab (unlocked by a key sequence in `MainDesktopKeys.kt`): the tab itself
(`CrosswordTab`, with its pieces in `CrosswordTabParts.kt`) and the decoder and layout engine for
the puzzles it plays (`data/CrosswordData.kt`). A real Gradle module of this build —
`include(":crossword-tab")`, `implementation(projects.crosswordTab)`. `:composeApp` is its only
consumer.

It takes `:strings`, `:theme` and `:settings` — and nothing of `:composeApp`'s, and **never
`:crossword`**, the authoring tool. See that module's `AGENT.md`: the decoder here mirrors its
encoder by hand, deliberately.

## The puzzles

`crossword/encoded/*.xwp` is the one source of truth. `syncCrosswordFiles` (in this module's build
file) copies them into `src/main/resources/crossword/` before `processResources`; the copy is
git-ignored. The tab reads them as plain classpath resources, so the module generates no `Res`
class of its own. Its UI strings come from `:strings`, in English only (crossword is not localised).

## Package

**`org.churchpresenter.crosswordtab`**, with `.data`.

## Rules

- `internal` stops at the module edge: what `:composeApp` calls is public, everything else is not.
- **Tests live here**, beside the code. The tab's screenshots stay in the app
  (`CrosswordTabScreenshotTest`), where they were recorded.
- Progress is debounced with a `delay`; the v2 test API runs it on the test clock, so tests advance
  `mainClock` rather than wait.

## Commands

```bash
./gradlew :crossword-tab:test :crossword-tab:detekt
./gradlew :crossword-tab:jacocoTestCoverageVerification
```
