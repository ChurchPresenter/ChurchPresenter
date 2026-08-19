# ChurchPresenter Presentation Engine — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`; the architecture, the public API and the package map are in this directory's
`README.md` — read it first.

## What it is

The parser and renderer for **PPTX (animated), PPT (static), PDF and Keynote (animated, via a
reverse-engineered IWA parser)**. `:composeApp` calls `PresentationLoader`, `DeckRasterizer`,
`TimelineEvaluator`, `SlideDiskCache`, `SlideFontRegistry` and the `model` types from
`viewmodel/PresentationViewModel.kt`, `presenter/PresentationPlayer.kt` and `server/CompanionServer.kt`.

## How it is wired in

A real Gradle module of this build: `include(":presentation-engine")`,
`implementation(projects.presentationEngine)`. It was the **last** module mounted into
`:composeApp` through `kotlin.srcDir` — its source used to compile as part of the app while it also
kept a Gradle wrapper of its own. Both are gone: there is one build, one wrapper, one place its
classes come from.

What that changed, and what it did not:

- **One build to satisfy.** No more "compile both builds" — `./gradlew :presentation-engine:build`
  is the whole story, and the app picks it up as a project dependency.
- **Zero Compose dependency, still by construction.** The module declares no Compose dependency, so
  an accidental Compose import fails to compile here even though the app around it is a Compose
  app. Fix the import; never add the dependency.
- **Everything runs in-JVM.** Never shell out to `osascript`, AppleScript, `qlmanage`, `sips` or
  `unzip`, on any platform. The pure-Java `aircompressor` snappy decompressor is in the dependency
  list for exactly this reason.
- **Its classes no longer land in the app's output directory**, so `:composeApp`'s JaCoCo report no
  longer has to filter them out — this module is measured by its own report and its own floor.
- CI runs `./gradlew :presentation-engine:test` and
  `:presentation-engine:jacocoTestCoverageVerification`, gated on the `engine` filter in
  `.github/workflows/test.yml`.

## Coverage

The module carries the root build's six-counter floor, but **not** at the usual 85%: it is a parser
and a rasterizer, some of it reachable only through real decks, and its output is pixels. The
floors in `build.gradle.kts` are its measured coverage rounded down —

```
INSTRUCTION 0.65   BRANCH 0.50   LINE 0.75   COMPLEXITY 0.45   METHOD 0.80   CLASS 0.85 (default)
```

— a **ratchet, not a target**: raise one as tests are added, never lower one to make a change fit.
`extra["coverageExcludes"]` drops `**/ui/**`, `**/MainKt*` and the CLI diagnostics (`**/*Dump*`,
`**/MakeSampleDeck*`).

**Where the remaining gap is**, in rough order of size: `KeynoteDeckParser`,
`KeynoteSceneRasterizer` and `KeynoteBuildMapper` (real IWA documents — `Fixtures` can build them
byte by byte, which is how the existing Keynote tests work), `PresentationLoader`'s native-Keynote
path, and `SlideFontRegistry`'s system-font scan. Everything pure — timeline evaluation and
compilation, the preset catalog, motion paths, the disk cache — is covered, so a new effect or a
new preset id has no excuse for arriving untested.

## Detekt

`./gradlew :presentation-engine:detekt` — the app's shared `config/detekt/detekt.yml`, **no
baseline**, main and test sources both in scope.

## Dependencies

They come from `gradle/libs.versions.toml`, which is now the only place their versions are written —
`:composeApp` and `:converter` resolve the same aliases, so there is nothing left to keep in sync by
hand:

- `libs.pdfbox`, `libs.apache.poi`, `libs.apache.poi.scratchpad`
- `libs.apache.poi.ooxml` **with `poi-ooxml-lite` excluded** plus `libs.apache.poi.ooxmlFull` — the
  animation timing parser needs the `<p:timing>` schema classes (`CTTLTimeNode*`,
  `CTTLAnimateBehavior`, …) that the lite jar omits. **Exactly ONE POI schema jar may be on the
  classpath**, here, in the app and in `:converter`; `:composeApp` additionally excludes the lite
  module graph-wide.
- `libs.aircompressor` — pure-Java snappy for the Keynote IWA reader.
- All POI/PDFBox access is **typed, no reflection**.

## Commands

From the repo root, on the root wrapper:

```bash
./gradlew :presentation-engine:test                              # the suite, headless-safe
./gradlew :presentation-engine:jacocoTestCoverageVerification    # the coverage floor
./gradlew :presentation-engine:dumpTiming  -Pfile=/path/deck.pptx [-Pout=/dir]   # parse audit + PNG renders
./gradlew :presentation-engine:dumpKeynote -Pfile=/path/deck.key                 # IWA object-graph probe
./gradlew :presentation-engine:makeSampleDeck -Pout=/path/sample.pptx            # animated test deck
```

`dumpTiming`, `dumpKeynote` and `makeSampleDeck` are CLI diagnostics — their `println`s are their
purpose, and are exempt from the no-debug-print rule (see `DEVELOPMENT_GUIDE.md`'s decision log).
The test task forwards `-DupdateGolden` for the golden-file suites; regenerate a golden only for an
intentional behavior change, and commit it with that change.

## The engine-wide invariant

**A slide never fails to show.** Unknown effects degrade to Fade, unrenderable Keynote slides gate
per-slide to a static fallback, whole-file failures fall back to a fully static deck — and every
degrade is recorded in `Deck.warnings` rather than thrown. `PresentationLoader.load` never throws;
it returns `LoadResult.Failure` with a `DeckLoadError`. Keep new code inside that contract.
