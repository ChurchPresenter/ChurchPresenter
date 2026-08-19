# ChurchPresenter Presentation Engine — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`; the architecture, the public API and the package map are in this directory's
`README.md` — read it first.

## What it is

The parser and renderer for **PPTX (animated), PPT (static), PDF and Keynote (animated, via a
reverse-engineered IWA parser)**. `:composeApp` calls `PresentationLoader`, `DeckRasterizer`,
`TimelineEvaluator`, `SlideDiskCache`, `SlideFontRegistry` and the `model` types from
`viewmodel/PresentationViewModel.kt`, `presenter/PresentationPlayer.kt` and `server/CompanionServer.kt`.

## It is the last mounted sub-build

Its source is compiled into the app through `kotlin.srcDir` in `composeApp/build.gradle.kts`, and
it **also** has its own Gradle build and wrapper here. Every other module has been promoted to a
real module of the root build; this one has not (yet).

Consequences, and they are the rules that matter here:

- **When you touch this code, compile BOTH builds**: `./gradlew compileKotlinJvm` at the repo root
  **and** `sh gradlew build` in this directory. The main build is more permissive and will accept
  code this module's own build rejects.
- **Zero Compose dependency, by construction.** An accidental Compose import compiles fine in the
  app build and fails the standalone build here. That is the guard working — fix the import, don't
  add the dependency.
- **Everything runs in-JVM.** Never shell out to `osascript`, AppleScript, `qlmanage`, `sips` or
  `unzip`, on any platform. The pure-Java `io.airlift:aircompressor` snappy decompressor is in the
  dependency list for exactly this reason.
- Its classes land in the **same output directory** as the app's, which is why
  `:composeApp`'s JaCoCo report restricts itself to `org/churchpresenter/**` — this module is
  measured by its own build.
- CI runs it as its own step (`chmod +x gradlew && ./gradlew test`), gated on the `engine` filter
  in `.github/workflows/test.yml`.

## Dependency sync

The presentation dependencies here are **mirrored in `composeApp/build.gradle.kts` and must stay in
sync** — the standalone build has to compile against the same versions the app runs against:

- `pdfbox:2.0.33`, `poi:5.3.0`, `poi-scratchpad:5.3.0`
- `poi-ooxml:5.3.0` **with `poi-ooxml-lite` excluded** plus `poi-ooxml-full:5.3.0` — the animation
  timing parser needs the `<p:timing>` schema classes (`CTTLTimeNode*`, `CTTLAnimateBehavior`, …)
  that the lite jar omits. **Exactly ONE POI schema jar may be on the classpath**, here, in the app
  and in `:converter`.
- `io.airlift:aircompressor` — pure-Java snappy for the Keynote IWA reader.
- All POI/PDFBox access is **typed, no reflection**.

## Commands

From this directory:

```bash
./gradlew test                                              # the suite, headless-safe
./gradlew dumpTiming  -Pfile=/path/deck.pptx [-Pout=/dir]   # parse audit + PNG renders
./gradlew dumpKeynote -Pfile=/path/deck.key                 # IWA object-graph probe
./gradlew makeSampleDeck -Pout=/path/sample.pptx            # animated test deck
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
