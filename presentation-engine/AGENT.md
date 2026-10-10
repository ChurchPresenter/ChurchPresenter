# ChurchPresenter Presentation Engine — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`; the architecture, the public API and the package map are in this directory's
`README.md` — read it first.

## What it is

The parser and renderer for **PPTX (animated), PPT (static), PDF and Keynote (animated, via a
reverse-engineered IWA parser)**. `:composeApp` calls `PresentationLoader`, `DeckRasterizer`,
`TimelineEvaluator`, `SlideDiskCache`, `SlideFontRegistry` and the `model` types from
`viewmodel/PresentationViewModel.kt` and `server/CompanionServer.kt`; `:live-output` calls them from
`PresentationPlayer`.

`:converter` is the module's other consumer, and it wants the opposite of pixels: `KeynoteText`
hands back a `.key`'s **words**, one string per slide, so the converter can write them out as
songs. It is deliberately the only text-shaped entry point — `Deck`/`Slide` carry `notes` and layer
geometry but no slide body text — and it exists so the IWA reader here is not written a second time
in a module that only needs the lyrics. It keeps the never-throws contract below: an unreadable
deck is an empty list. Its own fallback is the `QuickLook/Preview.pdf` Keynote embeds, which is how
an iWork '09 or password-protected document still yields its text.

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

The module carries the root build's six-counter floor, **85% on all six**, and names no
`extra["coverageFloors"]` of its own — every counter clears the default. Do not add one back to
make a change fit.
`extra["coverageExcludes"]` is the default `**/ComposableSingletons*` only — the CLI diagnostics
are measured too: `DumpTiming.dump`/`DumpKeynote.dump` take their output streams, and `DumpToolsTest`
drives them on fixture decks. Never add an exclude back.

**Where the remaining gap is**: `SlideFontRegistry`'s directory scan — it walks the machine's real
font directories and sits behind a one-shot JVM latch, so covering it deterministically means the
suite may only call `initialize` one way — plus the two tools' one-line `main`s, null-type arms of
`when` over XMLBeans/IWA values that the parsers cannot produce, and `catch` blocks around POI and
PDFBox calls. A new effect, a new preset id or a new timing
behavior has no excuse for arriving untested — `Fixtures` builds PPTX, PDF and IWA documents
programmatically, including `addRawTiming` for arbitrary `<p:timing>` XML.

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

## Package

**`org.churchpresenter.presentationengine`** (subpackages `pptx`, `keynote`, `pdf`, `model`,
`timeline`, `fonts`, `cache`, `tools` unchanged). It was `presentation.engine`, and the Gradle
`group` said the same.

**Rewrite on the two-segment prefix, never on `presentation.`** — `:composeApp` has a `presentation`
*variable* all over its schedule and dialog code (`presentation.slideCount`, `presentation.filePath`,
`presentation.typeIcon`, …). Matching the single segment retargets those at a package and the
failure surfaces as dozens of unrelated unresolved references.

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
