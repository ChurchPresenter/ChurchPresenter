# ChurchPresenter Presentation Engine

Parses and renders presentation files — **PPTX (animated), PPT (static), PDF, Keynote
(animated via a reverse-engineered IWA parser)** — entirely in-JVM, cross-platform, with no
external processes. Compiled into ChurchPresenter via `kotlin.srcDir` (see
`composeApp/build.gradle.kts`) and buildable/testable standalone.

## Public API

```kotlin
val deck = when (val r = PresentationLoader.load(file)) {   // never throws
    is LoadResult.Success -> r.deck
    is LoadResult.Failure -> /* r.error: PASSWORD_PROTECTED | EMPTY_DOCUMENT | … */
}
DeckRasterizer(deck, targetWidthPx = 1920).use { raster ->
    val jpegReady = raster.renderFinalFrame(slideIndex)     // all builds complete
    val layers = raster.rasterizeSlideLayers(slideIndex)    // transparent ARGB per layer
}
TimelineEvaluator(slide.timeline!!, deck.slideWidthPt, deck.slideHeightPt, bounds, hidden)
    .evaluate(stepIndex, elapsedMs)                         // pure: per-layer LayerState
```

`Deck` is pure description (no open resources); `Slide.timeline` is a click-driven step list
compiled from PPTX `<p:timing>` or Keynote build chunks; every effect samples to
`LayerState(alpha, translate, scale, rotation, clip)`.

**The engine-wide rule: a slide never fails to show.** Unknown effects degrade to Fade,
unrenderable Keynote slides gate per-slide to a static fallback (embedded preview PDF or
thumbnails), whole-file failures fall back to fully static decks. Every degrade lands in
`Deck.warnings`.

## Structure

| Package    | Contents |
|------------|----------|
| `model`    | Deck/Slide/LayerSpec, Timeline/Step/EffectInterval/EffectSpec/LayerState |
| `pptx`     | POI loaders, layer planner, per-shape/per-paragraph rasterizer, `<p:timing>` parser, transition parser |
| `timeline` | TimelineCompiler (behavior-first synthesis), PresetCatalog (degrade tables), TimelineEvaluator, motion-path flattener |
| `keynote`  | Dynamic protobuf reader, IWA chunk/object index, deck parser (whitelist + per-slide gate), scene rasterizer, build mapper, static fallback |
| `fonts`    | SlideFontRegistry: AWT registration, system-dir scan, POI font-handler substitution |
| `cache`    | SlideDiskCache: shared final-frame JPEG cache (manifest committed last, self-healing) |
| `tools`    | DumpTiming / DumpKeynote / MakeSampleDeck (gradle tasks of the same names) |

Keynote field numbers live in `keynote/KnFields.kt`, vendored from
[psobot/keynote-parser](https://github.com/psobot/keynote-parser) (protos 14.4) with source
citations. The dynamic wire reader ignores unknown fields, so Keynote schema drift degrades
instead of crashing; use `dumpKeynote` to triage a document that stops parsing.

## Commands

```bash
./gradlew test                                    # full suite, headless-safe
./gradlew dumpTiming  -Pfile=deck.pptx -Pout=/tmp/frames   # parse audit + PNG renders
./gradlew dumpKeynote -Pfile=deck.key             # IWA structure probe
./gradlew makeSampleDeck -Pout=sample.pptx        # animated test deck (builds + transitions)
```

Dependency versions are kept in sync with `composeApp/build.gradle.kts` (note: `poi-ooxml`
must exclude `poi-ooxml-lite` in favour of `poi-ooxml-full` — the timing schema classes are
lite-omitted, and only one schema jar may be on the classpath).
