# Agent Development Notes

This document tracks coding standards, common mistakes, and debugging notes for the ChurchPresenter project.

## Coding Standards

### 1. Import Statements
- ❌ **NEVER** use wildcard imports like `import churchpresenter.composeapp.generated.resources.*`
- ✅ **ALWAYS** use explicit imports for each resource
- ❌ **NEVER** use fully qualified type names inline (e.g. `org.churchpresenter.app.churchpresenter.data.AnnouncementsSettings`) when the type can simply be imported at the top of the file
- ✅ **ALWAYS** add an `import` statement and use the short name

### 2. String Resources
- ❌ **NEVER** use hard-coded strings in user-facing UI
- ✅ **ALWAYS** use string resources from `Res.string.*`
- 📝 **OK** to use constants for internal/non-user-facing strings

### 2a. Translations — **CRITICAL RULE**
- ❌ **NEVER** add, update, or look up translations in `values-ru/`, `values-uk/`, `values-pl/`, `values-de/`, `values-be/`, `values-cs/`, `values-kk/`, or any other non-English locale file
- ❌ **NEVER** attempt to translate strings into other languages unless the user **explicitly** says "get translations" or "translate"
- ✅ **ONLY** add new strings to the default English `values/strings.xml`
- ✅ Leave all other locale `strings.xml` files untouched unless instructed
- **Reason**: Translations are managed separately and incorrect machine translations cause quality issues

### 3. Material Design
- ✅ **ALWAYS** use Material 3 (`androidx.compose.material3.*`)
- ❌ **AVOID** mixing Material 2 components

### 4. Type Annotations
- ❌ **AVOID**: `width: androidx.compose.ui.unit.Dp = 120.dp`
- ✅ **PREFER**: `width: Dp = 120.dp`

### 8. ViewModel Ownership — **CRITICAL RULE**
- ❌ **NEVER** pass a ViewModel as a parameter to another class, tab, or ViewModel:
  ```kotlin
  // WRONG — SongsViewModel does not belong here
  fun MainDesktop(songsViewModel: SongsViewModel, ...)
  class PicturesViewModel(scheduleViewModel: ScheduleViewModel)
  fun ScheduleTab(songsViewModel: SongsViewModel, ...)
  ```
- ❌ **NEVER** pass a ViewModel OUT of the class it is defined in via callbacks, return values, or any other mechanism:
  ```kotlin
  // WRONG — ViewModel must not leave the composable that owns it
  onViewModelReady: (SongsViewModel) -> Unit   // exposing the VM itself
  fun getViewModel(): SongsViewModel            // returning the VM
  var externalRef = songsViewModel              // storing reference outside
  ```
- ✅ **ViewModels must ONLY be created and used inside the class/composable they manage**:
  ```kotlin
  // CORRECT — SongsTab owns its own ViewModel, nothing leaves
  @Composable
  fun SongsTab(...) {
      val viewModel = remember { SongsViewModel(appSettings) }
  }
  ```
- ✅ **Pass data OUT via typed callbacks or state parameters — never the ViewModel itself**:
  ```kotlin
  // CORRECT — expose actions via typed callbacks
  fun SongsTab(onAddToSchedule: (songNumber: Int, title: String, songbook: String) -> Unit)
  // CORRECT — receive selected item as state, react with LaunchedEffect
  fun SongsTab(selectedSongItem: ScheduleItem.SongItem? = null)
  // CORRECT — expose data via Flow/StateFlow on the ViewModel (consumed internally)
  val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
  ```
- **The only acceptable exception**: a technical rendering bridge like `MediaPresenter`/`VideoPlayer` where the JFX/Swing panel lifecycle is tightly coupled to the ViewModel. Even then, document it explicitly.
- **Reason**: ViewModels passed around create tight coupling between unrelated components, make testing impossible, cause memory leaks, and violate single-responsibility. Each composable/class should only know about its own ViewModel.

### 7. Fully-Qualified Type Names — **NEVER DO THIS**
- ❌ **NEVER EVER** use fully-qualified type names when the type can be imported:
  ```kotlin
  // WRONG — never do this
  fun updateSong(oldSong: org.churchpresenter.app.churchpresenter.data.SongItem): Boolean
  ```
- ✅ **ALWAYS** add an import at the top of the file and use the short name:
  ```kotlin
  // CORRECT
  import org.churchpresenter.app.churchpresenter.data.SongItem
  fun updateSong(oldSong: SongItem): Boolean
  ```
- **This applies everywhere**: function parameters, return types, variable declarations, generic type arguments — no exceptions.

### 5. Code Cleanup
- 📌 **TODO**: Clean up unused code and imports
- 📌 **TODO**: Remove redundant code where possible

### 6. UI Icons and Buttons
- ❌ **NEVER** use text-based icon emojis like:
  ```kotlin
  Text(
      text = if (isPlaying) "⏸" else "▶",
      style = MaterialTheme.typography.headlineLarge,
      color = Color.White
  )
  ```
- ✅ **ALWAYS** use proper image buttons with actual icon resources:
  - Use `painterResource()` for bundled icons
  - Use custom icon files in resources
  - Use icon libraries if available
- **Reason**: Text-based emojis are inconsistent across platforms, not scalable, and look unprofessional
- **Example location**: See `PicturesTab.kt` lines 186-190 for what NOT to do

## Debugging Guidelines

### When Adding Debug Logs
1. ✅ **ALWAYS** keep logs until fix is confirmed working
2. ✅ **ASK** before removing logs if uncertain
3. ✅ **DOCUMENT** what was learned from the logs

## Feature Implementation Notes

### Presentation Schedule Integration (February 2026)
**Implemented**: Add to Schedule functionality for presentations

**Components Added**:
1. **ScheduleItem.PresentationItem** - New sealed class variant for presentations
   - Stores: filePath, fileName, slideCount, fileType
   - Icon: 📊 (pie chart emoji)

2. **ScheduleViewModel.addPresentation()** - Method to add presentations to schedule
   - Parameters: filePath, fileName, slideCount, fileType
   - Auto-generates unique ID

3. **PresentationViewModel.loadPresentationByPath()** - Load presentations from file path
   - Used when clicking presentation items in schedule
   - Loads slides and switches to presentation tab

4. **Schedule Tab Integration**:
   - Handles PresentationItem clicks (switches to Presentation tab)
   - Handles "Go Live" button for presentations (TODO: actual presentation display)
   - Shows file type and path as secondary text

5. **Main Desktop Integration**:
   - Passes PresentationViewModel to ScheduleTab
   - Handles tab switching when clicking presentation schedule items

**Files Modified**:
- `models/ScheduleItem.kt` - Added PresentationItem
- `viewmodel/ScheduleViewModel.kt` - Added addPresentation()
- `viewmodel/PresentationViewModel.kt` - Added loadPresentationByPath()
- `tabs/PresentationTab.kt` - Wired up Add to Schedule button
- `tabs/ScheduleTab.kt` - Added PresentationItem handling
- `MainDesktop.kt` - Connected presentation loading from schedule

**Build Status**: ✅ Compiles successfully

**Next Steps** (TODO):
- Implement actual slide presentation in presenter window
- Add Presenting.PRESENTATION enum value
- Create PresentationPresenter composable for display window
- Test with various file formats (PPT, PPTX, KEY, PDF)

### Media Settings Tab (February 2026)
**Renamed**: PictureSettingsTab → MediaSettingsTab

**Purpose**: Share slideshow and transition settings between Pictures and Presentations tabs

**Changes Made**:
1. **File renamed**: `PictureSettingsTab.kt` → `MediaSettingsTab.kt`
2. **Tab label updated**: "Pictures" → "Media" in OptionsDialog
3. **Section header**: "Slideshow Settings" → "Media Slideshow Settings"
4. **Settings scope**: Now applies to both Pictures and Presentations
   - Auto-scroll interval
   - Loop checkbox
   - Transition duration
   - Animation type (Crossfade, Fade, Slide Left, Slide Right, None)

**Settings Storage**: Still uses `pictureSettings` in AppSettings (shared for all media types)

**Build Status**: ✅ Compiles successfully

### PresentationViewModel Media Settings Integration (February 2026)
**Implemented**: PresentationViewModel now uses Media Settings for auto-play and looping

**Changes Made**:
1. **PresentationViewModel Constructor**: Now accepts optional `AppSettings` parameter
   - Reads `autoScrollInterval` from `pictureSettings` (default: 5 seconds)
   - Reads `isLooping` from `pictureSettings` (default: true)
   
2. **Auto-play Properties Added**:
   - `autoScrollInterval: Float` - Time between slides during auto-play
   - `isLooping: Boolean` - Whether to loop back to first slide after last

3. **Slide Navigation Updated**:
   - `nextSlide()`: Loops to first slide when at end if looping enabled
   - `previousSlide()`: Loops to last slide when at beginning if looping enabled

4. **PresentationTab Enhanced**:
   - Added `LaunchedEffect` for auto-play functionality
   - Uses `viewModel.autoScrollInterval` for timing
   - Uses `viewModel.isLooping` for loop behavior
   - Consistent behavior with PicturesTab

5. **Main.kt Updated**:
   - `PresentationViewModel` now instantiated with `appSettings` parameter
   - Uses `remember(appSettings)` to recreate when settings change

**Settings Shared**:
- Both PicturesTab and PresentationTab use the same Media Settings:
  - Auto-scroll interval (1-30 seconds)
  - Loop enabled/disabled
  - Transition duration (future: for animations)
  - Animation type (future: for slide transitions)

**Build Status**: ✅ Compiles successfully

## Dependencies

### Presentation dependencies (PDFBox / POI / aircompressor)
- 📦 In `composeApp/build.gradle.kts` **and mirrored in the engine sub-build**
  (`ChurchPresenter-PresentationEngine/build.gradle.kts` — keep versions in sync):
  - `org.apache.pdfbox:pdfbox:2.0.33`
  - `org.apache.poi:poi:5.3.0`, `poi-scratchpad:5.3.0`
  - `org.apache.poi:poi-ooxml:5.3.0` **with `poi-ooxml-lite` excluded** +
    `org.apache.poi:poi-ooxml-full:5.3.0` — the animation timing parser needs the `<p:timing>`
    schema classes that lite omits; exactly ONE schema jar may be on the classpath
  - `io.airlift:aircompressor` — pure-Java snappy for the Keynote IWA reader (no natives)
- All POI/PDFBox access is **typed, no reflection** (the old reflection wrappers are gone —
  these are plain compile-time dependencies).

### Keynote loading (see Presentation Engine section below for the full picture)
- ⚠️ **NEVER use osascript, AppleScript, qlmanage, sips, `unzip`, or any external process** —
  everything runs in-JVM (`java.util.zip`, aircompressor). The old macOS Keynote.app/qlmanage
  export paths were removed in July 2026.
- Primary path: **native IWA parse** (`presentation.engine.keynote.*`) — renders and animates
  whitelisted content itself.
- Static fallback (whole-file parse failure, or per-slide whitelist gate): embedded
  `QuickLook/Preview.pdf` (full-res, most .key files) → `Data/st-*.jpg` slide thumbnails
  ordered by the `Index/Slide-<id>.iwa` zip-order heuristic. Some documents (verified on a
  real file) have NO Preview.pdf — thumbnails are the only static source there.

## Presentation Engine — Animated PPTX + Keynote Playback (July 2026)

**What changed**: the Presentation feature was overhauled from "everything becomes a static
JPEG via reflection-wrapped POI/PDFBox, duplicated between PresentationViewModel and
CompanionServer" into a standalone engine sub-build with **true animation playback**:
PowerPoint entrance/emphasis/exit effects, by-paragraph text builds, click-step sequencing,
motion paths, slide transitions — and the same for Keynote via a reverse-engineered IWA parser.
All in-JVM, all three platforms, no external processes.

### Module: `composeApp/src/jvmMain/appResources/common/ChurchPresenter-PresentationEngine/`
BLE-pattern sub-build (own gradle project, JUnit5 tests, `kotlin.srcDir`-mounted into
composeApp). Package root `presentation.engine`. **Zero Compose dependency by construction**
(its own classpath has no Compose — accidental imports fail the standalone build).
- `./gradlew test` — full suite (31+ tests), headless-safe (`java.awt.headless=true`)
- `./gradlew dumpTiming -Pfile=/path/deck.pptx [-Pout=/dir]` — parse audit + PNG renders of
  any deck; every degrade prints, so user reports become PresetCatalog data entry
- `./gradlew dumpKeynote -Pfile=/path/deck.key` — IWA object-graph probe (schema drift triage)
- `./gradlew makeSampleDeck -Pout=/path/sample.pptx` — generates an animated test deck

### Architecture (engine)
- **Model** (`model/`): `Deck → Slide → LayerSpec` (Background z-bands / Shape / ParagraphText /
  StaticComposite) + `Timeline → Step → EffectInterval → EffectSpec` (all effects reduce to
  sampled `LayerState`: alpha/translate/scale/rotate/RevealClip). `Slide.fidelity`:
  NATIVE vs STATIC_FALLBACK (per-slide degrade).
- **`PresentationLoader.load(file)`** — parse only, never throws; `DeckRasterizer` (open-once,
  AutoCloseable) produces pixels: `renderFinalFrame` (all builds complete — grid, cache,
  companion) and `rasterizeSlideLayers` (transparent ARGB per-layer bitmaps for playback).
- **PPTX**: `AnimationTargetScanner` (spTgt scan) → `PptxLayerPlanner` (z-band flattening;
  group targets animate the whole group) → `TimingParser` (typed CTTL* over poi-ooxml-full,
  document-order cursor walk) → `TimelineCompiler` (behavior-first synthesis: anim curves /
  animEffect filters / animMotion paths beat the preset table; `PresetCatalog` = filter map +
  preset-id backstop; **unknown → Fade, never a missing shape**). Per-paragraph layers use
  reversible transparent-fill mutation on run rPr elements (NEVER replace paragraph XML
  wholesale — that disconnects POI's cached wrappers: XmlValueDisconnectedException).
  Tested invariant: compositing all layers == plain `slide.draw` (perceptual), and the
  mutation restores pixel-identically.
- **Keynote** (`keynote/`): hand-rolled protobuf wire reader (`ProtoReader`; unknown fields
  ignored → version drift degrades, never crashes) + `IwaChunkReader` (raw-snappy chunks) +
  `ObjectIndex` (all iwa objects + Metadata data-id→file map). Field numbers vendored in
  `KnFields.kt` with proto citations (extracted from psobot/keynote-parser 14.4).
  `KeynoteDeckParser` walks Document→Show→slide nodes→drawables (whitelist: images, shapes
  incl. bezier paths, text, groups; movies/charts/tables/masked images **gate the slide** to
  static fallback), builds (`KeynoteBuildMapper` → the same EffectSpec model; buildChunks
  drive click steps), transitions, notes. `KeynoteSceneRasterizer` lays text out itself
  (LineBreakMeasurer over `SlideFontRegistry`).
  Empirically validated against a real .key: geometry `flags` is NOT a flip bitmask (plain
  drawables carry flags=3); `drawables_z_order` omits placeholders (render them below it);
  auto-sized text boxes persist size (0,0) → lay out unwrapped from the anchor.
  Known gap: theme-preset fills (stylesheet presets, not the parent chain) don't resolve →
  decorative shapes can be invisible; text/images/explicit fills are correct.
- **Fonts** (`fonts/SlideFontRegistry.kt`): bundled fonts registered into AWT at startup
  (main.kt daemon thread via `LottieFonts.bundledFontResources()`), platform font dirs scanned
  for families the JVM misses (per-user Windows fonts), POI `Drawable.FONT_HANDLER` hook
  substitutes unavailable families (Calibri→Carlito→…→Open Sans). PPTX-embedded fntdata parts
  register per-document. `LottieFonts` delegates its system-font lookup here.
- **Cache** (`cache/SlideDiskCache.kt`): `~/.churchpresenter/slides/<id>/` schema v2 —
  `manifest.json` written LAST as the commit marker (crashed render self-heals), final-frame
  JPEGs + notes. **Shared by PresentationViewModel and CompanionServer** (one render, both
  consumers; server accepts any width, writes at default 1920). Layers are rasterized lazily
  in memory (window of current±1 slide), deliberately not persisted.

### Architecture (app side)
- `presenter/PresentationPlayer.kt` — rendering bridge owned by PresenterManager (LottieFrameStream
  pattern, documented ViewModel-rule exception): owns the Deck, layer-bitmap window, click-step
  state machine (`advance`/`rewind`; step -1 = pre-click state), deck-transition overlay.
- `PresenterManager`: `presentationFrame: State<PresentationFrame?>`, `presentationShowSlide(deck, idx)`
  (player only when the deck has any timeline/transition — static decks keep the old
  AnimationType path), `advancePresentationStep()/rewindPresentationStep()`,
  `runPresentationClock()` (withFrameNanos, driven from main.kt beside the Lottie clock; idles
  when settled). Leaving PRESENTATION mode releases the player.
- `presenter/PresentationPresenter.kt` — Canvas compositor for every output (main windows ×6
  branches, LivePreviewPanel, BrowserSourceVideoRenderer): letterbox, per-layer
  alpha/transform/clip, deck transitions (FADE/PUSH/WIPE/SPLIT/COVER), KEY-role white fill,
  freeze + null-frame → falls back to the static `SlidePresenter` body.
- `PresentationTab`: next/prev/keyboard/auto-play route through the step machine first
  ("Build x of y" indicator + per-thumbnail step-count badge); thumbnails decode via
  `produceState` on IO (jank fix). MainDesktop's remote-select/instance-link handlers keep the
  player in sync (or cleared) so a stale animated frame can never override a pushed slide.
- Settings: `PresentationSettings.animateKeynote` (default true; MediaSettingsTab checkbox) —
  one-click fallback to static .key rendering since that parser is reverse-engineered.

### Key rules
- A slide must NEVER fail to show: every unknown effect degrades to Fade, every unrenderable
  Keynote slide gates to its static fallback, whole-file parse failures fall back to the
  static cascade. Degrades are logged as deck warnings → CrashReporter breadcrumbs.
- Kotlin block comments NEST: never write paths like `Index/*.iwa` inside a KDoc.
- JPEG writes must go through `DeckRasterizer.flattenToRgb` (ImageIO's JPEG writer corrupts
  ARGB input).

### Verification status
- Engine: 31+ unit/invariant tests green (timing goldens, evaluator math, layer-composite
  equivalence, mutation purity, IWA round-trips); PPTX pipeline validated end-to-end on a
  generated animated deck (`dumpTiming`: 4 click steps, transitions, zero degrades); Keynote
  validated against a real-world .key (all text/layout correct in PNG output, side-by-side
  with Keynote's own thumbnail).
- App: compiles zero-warning; 40 s launch smoke test clean. Arrow-key slide navigation
  verified hands-on in the running GUI (System Events keystrokes + screenshots): tab
  focus after keyboard tab-switch, after loading a deck via mouse click, both directions.
  Two fixes landed from that session: (1) `focusRequester.requestFocus()` now fires on every
  `loadGeneration` change and on thumbnail click — previously only the schedule-item path
  requested focus, so decks opened via the file dialog or recents bar had dead arrow keys;
  (2) `advancePresentationStep`/`rewindPresentationStep` are identity-guarded (player must be
  showing exactly the caller's deck+slide, and the display must not be cleared) so the build
  step machine can never silently eat arrow presses. Go-live of an animated deck on a real
  output window verified hands-on (July 10, `makeSampleDeck` deck, System Events + screenshots):
  pre-click state hides entrance targets (player active, not the static frame), all 4 build
  steps advance AND rewind with the "Build x of y" indicator tracking, mid-fade alpha visibly
  ~50%, PUSH mid-frame shows both slides at the seam, FADE mid-frame cross-fades both titles,
  step-count badges on thumbnails match `dumpTiming`, the browser-source preview mirrors the
  animated frame ("Live" badge), and Clear Display drops to the background with no stale frame.
  Also fixed from that session: `presentation_static_note` (strings.xml) still claimed
  animations/transitions are unsupported. **Not yet verified hands-on**: KEY-role output,
  freeze during playback, DeckLink, and the browser-source overlay page in OBS itself.

## Keynote Video Playback + Transition Fidelity (July 2026)

**Problem**: a user-provided stress-test `.key` (11 slides, each exercising a different Keynote
slide transition — Swoosh, Push, Dissolve+Flip, Scale+3D-cube, Twist, Swing×2, Reflection,
Confetti — and a final slide with an embedded `.mov`) rendered visibly differently than real
Keynote.app. Root causes, found by unzipping the file directly and cross-referencing the engine
source: (1) `KeynoteDeckParser` unconditionally gated any slide containing a movie drawable to
the static Keynote-exported thumbnail — the video slide's title, builds and transition were all
silently dropped for one frozen JPEG; (2) `KeynoteBuildMapper.mapTransition()` only mapped
Push/Fade/Wipe/Cover — 7 of the 8 real transitions in the file fell through to a blanket Fade;
(3) a couple of build-effect names (`pivot-build`, `bc-drop`) matched no keyword and silently
degraded to Fade despite better primitives existing.

**Fix — movie playback** (real field numbers confirmed via a `dumpKeynote` extension before
writing any parser code, not guessed: field 14 = movie data reference, field 15 = poster image
reference, both validated by resolving to the file's actual `Data/` entries):
- `KnFields.kt` — new `TSD.MovieArchive` field section (`MOVIE_SUPER`/`MOVIE_DATA`/`MOVIE_POSTER`).
- `KeynoteScene.kt` — new `KnDrawable.Movie` (geometry + `videoFile` + nullable `posterFile`).
- `KeynoteDeckParser.parseMovie()` replaces the old gate, modeled on `parseImage()` — gates only
  when the movie's own asset can't be resolved, never on a missing poster.
- `LayerSpec.Media` (pre-existing, `model/Deck.kt`, never actually constructed anywhere before
  this) gained `shapeIndex`/`contentRectPt` and is now built by `KeynoteLayerPlanner` for every
  top-level movie drawable — forced into its own layer even without a build targeting it.
  `PresentationLoader.kt`'s remap-hidden-layer step had to be extended from `is LayerSpec.Shape`
  to also cover `Media` (it silently discarded the whole layer list back to a flat static
  composite otherwise whenever a slide had a movie but no other timeline).
- `KeynoteSceneRasterizer` rasterizes the Media layer's poster with the same crop/offset math as
  `Shape`, and gained `extractDataFile()` — directory-bundle `.key` returns the real file, zip
  bundles extract once to a cached temp file, deleted in `close()` (not left to `deleteOnExit()`,
  which would leak large `.mov` temp files for the whole app run).
- New `presenter/KeynoteMovieDecoder.kt` (app-side, NOT the `SharedVideoOutput` singleton that
  stays scoped to the Media tab's one master video) — copies `SoftwareVideoPlayer`'s
  `CallbackVideoSurface`→`BufferedImage` technique, but blits each decoded frame into a mutable
  copy of the cached poster canvas at the pre-computed content rect, so every published bitmap is
  pixel-identical in size/offset to the poster it replaces — `PresentationPresenter.kt`'s
  `drawLayer()` needed zero changes as a result (already bitmap-source-agnostic).
  `createMediaPlayerComponent()`/`Component.mediaPlayer()`/`Component.releasePlayer()` in
  `VideoPlayer.kt` widened from `private` to `internal` for reuse.
- `PresentationPlayer.kt` owns one decoder at a time: `showSlide()` tears it down the instant the
  target layer changes (synchronously, from the Deck model — never waits on the new slide's async
  rasterization, so a decoder can't keep playing audio for a slide that's no longer showing);
  `frame()` lazily (re)constructs it once that slide's poster has actually rasterized.

**Fix — a real visibility/semantics bug found only by testing hands-on**: the first working build
hid the video's poster entirely until the "movie-start" build's click, because
`animation_type="In"` from the IWA defaulted to `EffectSpec.Role.ENTRANCE` — but Keynote's movie
start/pause/stop builds are *actions*, not entrances: the poster is visible on the slide from the
moment it appears, and the build only gates *playback*, not visibility (confirmed by comparing
against the file's own Keynote-rendered slide thumbnail, which shows title+poster together).
`KeynoteBuildMapper` now special-cases `effect.contains("movie")` to force `Role.EMPHASIS` ahead
of the type-string check, and maps it to `EffectSpec.Appear` instead of the default `Fade` (Appear
is a constant "present" state — no alpha ramp — vs. Fade's 0→1 animation, which would otherwise
still flash the already-visible poster in on click). Since the layer is no longer entrance-gated,
`PresentationPlayer` can no longer key playback start off `LayerState.visible`; it now caches
`movieStepIndex` (the click step whose build targets the movie layer, computed synchronously from
`Deck.slides[i].timeline` when the target layer changes) and gates `resume()`/`pause()` on
`stepIndex >= movieStepIndex` instead.

**Fix — transition/build coverage**: `mapTransition()` routes Swoosh/Swing/Twist/Reflection to
`COVER` (incoming content displaced onto the frame from a direction — much closer to the real
motion than a flat cross-fade) ahead of the final Fade catch-all; Confetti and pure Scale/Cube
stay on Fade per the pre-existing "no motion equivalent" philosophy (unchanged, still correct).
`mapEffect()` gained `pivot`→Spin and `drop`→`Fly(DOWN)`.

**Verification**: engine `./gradlew test` green throughout (compiled/tested after each stage,
engine-only changes never touched app code); `dumpTiming` against the actual file confirmed "No
degrade warnings — full coverage for this deck" and every transition/build mapping by hand.
Hands-on in the running GUI (System Events + screenshots, VLC installed via
`brew install --cask vlc` specifically to enable this — wasn't present before): all 11 slides
render live instead of frozen thumbnails; the video slide's poster is visible immediately in the
pre-click state (`Build 0 of 1`) and the embedded video plays with audio once the build step is
reached, confirmed by the user. A stale `~/.churchpresenter/slides/<hash>/` disk-cache entry from
before the fix (likely from the user's own earlier preview of the file) kept the thumbnail grid
showing the old blank render even after the fix landed — cleared manually; worth remembering that
`SlideDiskCache` entries don't invalidate on app-code changes, only on `sourceMtime`.

**Known gaps not addressed** (not exercised by this file — plain title+body slides, no custom
shapes/images/gradients/flips found): flip-bitmask disabled, theme-preset fills unresolved,
auto-sized text alignment/vertical-anchor, gradient-flattens-to-first-stop, rounded-corner loss,
editable-bezier degrade, per-paragraph/word build delivery. All pre-existing, already documented
above in this file — not regressions from this work.

**Files Modified**: `KnFields.kt`, `KeynoteScene.kt`, `KeynoteDeckParser.kt`,
`KeynoteLayerPlanner.kt`, `KeynoteSceneRasterizer.kt`, `model/Deck.kt`, `PresentationLoader.kt`,
`KeynoteBuildMapper.kt`, `tools/DumpKeynote.kt` (movie field probe) — all engine-side;
`presenter/KeynoteMovieDecoder.kt` (new), `presenter/PresentationPlayer.kt`,
`composables/VideoPlayer.kt` (visibility widening only) — app-side; `strings.xml`
(`presentation_static_note` no longer claims embedded video is unsupported).

### Follow-up crash: Go Live slide-preload race (same day)
A fifth unguarded `readBytes()` call site survived the July 10 disk-cache-race fix (commit
`736d612f`, which guarded four sites in `PresentationTab.kt`) — the "Go Live" button's own slide
preload (`GoLiveButton.onClick`, ~line 494-503) still called `f.readBytes()` unguarded. Deleting a
stale `~/.churchpresenter/slides/<hash>/` cache entry (while the app was live, testing the video
poster fix above) hit exactly this race and crashed the app with an uncaught
`FileNotFoundException` on `AWT-EventQueue-0`. Wrapped both reads in the same
`try/catch → null` pattern as the other five sites — all 7 `readBytes()` call sites in
`PresentationTab.kt` are now consistently guarded.

## Cross-Format Animation Consistency: PPTX Transition Fidelity + Keynote Per-Paragraph Builds (July 2026)

**Problem**: text and slide-transition animations didn't look similar between `.ppt` and `.key`
files. Investigated using the same file pair as the video-playback work above
(`RandomPresentation.key`/`.pptx`, exported minutes apart specifically for this comparison) plus
direct inspection of both files' raw XML/IWA data.

**Root cause 1 (the dominant one) — PPTX transitions were silently dropped for 10 of 11 slides.**
`TransitionParser`'s typed `isSetTransition`/`.transition` accessor only sees a `<p:transition>`
that's a *direct* child of `<p:sld>` — but any PowerPoint-2010+ transition (flip/warp/prism/…) is
wrapped in `<mc:AlternateContent><mc:Choice Requires="p14">`…richer…`</mc:Choice><mc:Fallback>`…
plain…`</mc:Fallback></mc:AlternateContent>` for backward compatibility, and `isSetTransition`
returns `false` for every one of those — confirmed via debug logging against the real deck (10/11
slides, zero warning, the transition just silently vanished). Rewrote `TransitionParser` to search
inside the wrapper via `XmlCursor` when the direct check misses, extracting the transition's own
attributes and its single content-child's name/direction as **plain strings** rather than through
POI's typed choice-content accessors — those resolve `isSetPush`/`isSetWipe`/etc. as `false` even
on a correctly retyped, standalone-reparsed fragment (tried `Factory.parse`, tried
`changeType()`, both failed the same way; `changeType()` on a still-cursor-live object also
silently corrupts the cursor's remaining `toNextSelection()` iteration — a real trap, don't retry
either approach) — raw text extraction sidesteps the whole binding problem and is exactly as
reliable for this shape (one wrapper, at most one simple self-closing child).

**Root cause 2 — even where the transition WAS read, duration was wrong.** The same
`mc:AlternateContent` transitions always carry a precise `p14:dur` extension attribute (real
milliseconds) alongside the legacy `spd` 3-bucket enum (slow/med/fast → 1000/750/500ms) for
readers that predate the extension — `p14:dur` was being discarded entirely. Real deck: `spd="slow"`
on every transitioned slide (→ flat 1000ms), but `p14:dur` ranging 1200-2900ms, matching the
Keynote counterpart's actual durations almost exactly (confirming both files came from the same
source timing). Now reads `p14:dur` via cursor when present, falling back to the `spd` bucket only
when it's genuinely absent (own cursor-positioning gotcha: a cursor from a document-level parse
starts at STARTDOC, not the element itself — `getAttributeText` silently finds nothing until
`toFirstContentToken()` is called first).

**Root cause 3 (smaller) — mismatched defaults.** When a build/transition duration is truly
unspecified in the source (rare — both formats' authoring tools almost always write one), Keynote
fell back to 700ms (`KeynoteBuildMapper`'s `?: 0.7`) vs PPTX's 500ms
(`TimelineCompiler.DEFAULT_EFFECT_DUR_MS` / `TransitionParser`'s fast/unset bucket). Aligned
Keynote's fallback to 500ms in both `mapEffect` and `mapTransition`.

**Feature — Keynote per-paragraph/bullet text builds** (user-requested, larger scope; the sample
file pair doesn't exercise it, told the user this before proceeding since neither file actually
uses paragraph-range builds — real Keynote.app isn't installed here either, so exact delivery
strings are provisional, same "validated per-deck via DumpKeynote" convention already used
elsewhere in this file for direction constants). PowerPoint can stagger a text build
paragraph-by-paragraph (multiple click steps); Keynote always animated a whole text box as one
click — an explicitly documented gap (`KeynoteBuildMapper`'s old "WS6 text work" comment).
`BUILD_DELIVERY` (`KnFields.kt`, already defined, previously only read by the `dumpKeynote` debug
tool) now gets consumed: `KeynoteBuildMapper.map()` gained a `drawables` parameter (cross-references
the target's already-parsed paragraph count), detects "paragraph"/"bullet" in the delivery string,
and synthesizes one `EffectInterval` per paragraph — each its own click step (matches Keynote's
real on-stage behavior) — instead of one whole-drawable interval.
`KeynoteLayerPlanner.plan()` emits one `LayerSpec.ParagraphText` per paragraph for those drawables
— **the exact same layer kind PPTX's planner already produces**, reused rather than reinventing a
new model type. `KeynoteSceneRasterizer` gained a `ParagraphText` case; `drawParagraphs()`'s
measure-and-draw loop was refactored into a shared `layOutParagraph(..., paint: Boolean)` so the
whole-object render and the new per-paragraph render can never drift out of sync — simpler than
PPTX's approach (which must mutate run XML through opaque POI drawing code to isolate one
paragraph), since Keynote already does its own text layout.
`PresentationLoader.kt`'s hidden-layer remap (already extended once this session for `Media`) needed
the same `ParagraphText` branch — otherwise a paragraph build's ENTRANCE role wouldn't correctly
hide it pre-click, the same bug class already hit and fixed for movie layers.

**Verification**: `TransitionParser`'s fix confirmed via `dumpTiming` against the real file —
before: 1 of 11 slides had any transition, all durations flat-bucketed; after: 10/11 slides show
their real transition (`PUSH/LEFT` correctly detected for the one non-exotic type; flip/warp/prism
correctly still degrade to `FADE`, matching the "no faithful compositor implementation" policy —
genuinely no equivalent, not a bug) with precise 1200-2900ms durations matching Keynote's. New
`KeynoteParagraphBuildTest.kt` (4 tests, engine suite): delivery detection + per-step interval
synthesis via a hand-built synthetic IWA fixture (promoted `ProtoWriter`/`buildIwa` from
`KeynoteIwaTest.kt` into shared `Fixtures.kt` for this), layer emission + `remapTimeline`
pass-through via hand-built `KnSlide` (no IWA needed for that half) — confirmed the
all-at-once/whole-object path is completely unaffected (regression-checked against
`RandomPresentation.key`'s own dumpTiming output, byte-identical before/after). Hands-on: both
decks load and go live without crashing; the video-slide feature from the session above (poster
visible pre-click, plays on build) still works correctly — no regression from any of this.

**Files Modified**: `pptx/TransitionParser.kt` (rewritten), `keynote/KeynoteBuildMapper.kt`,
`keynote/KeynoteLayerPlanner.kt`, `keynote/KeynoteScene.kt`, `keynote/KeynoteDeckParser.kt`,
`keynote/KeynoteSceneRasterizer.kt`, `PresentationLoader.kt` — all engine-side;
`test/.../Fixtures.kt`, `test/.../KeynoteIwaTest.kt` (promoted helpers, no behavior change),
`test/.../KeynoteParagraphBuildTest.kt` (new).

## PPTX Embedded Video Playback (July 2026)

**Problem**: PowerPoint embedded videos never played — the slide showed only the static poster
forever. The PPTX-side counterpart to the Keynote movie work above; same file pair
(`RandomPresentation.pptx`/`.key` both carry the identical video, `ppt/media/media1.mov` /
`Data/IMG_3840-9137.mov`).

**Root cause, confirmed against the real file's raw XML, not guessed**: a pptx video shape is a
plain `XSLFPictureShape` — no dedicated video-shape class exists in POI — carrying
`isVideoFile()`/`getVideoFileLink()` (a relationship id) alongside its ordinary `getPictureData()`
(the poster). Neither `PptxLayerPlanner` nor `PptxSlideRasterizer` had any video awareness at all;
`LayerSpec.Media` (the shared model already built for Keynote) was never constructed for PPTX —
`PptxSlideRasterizer.kt` explicitly threw if it was ever reached. Worse, the file's own
click-to-play trigger — `<p:cmd type="call" cmd="playFrom(0.0)">` inside a click-triggered
`<p:par>` (`presetClass="mediacall"`), `dur="24753"` matching the movie's real 24.75s length
exactly — was being silently dropped: `TimingParser.kt` had an explicit
`// audio, video, cmd — media/verb behaviors, not visual: skipped` catch-all. Because the
command's targeting uses the exact same `<p:tgtEl><p:spTgt>` structure as every other behavior,
`AnimationTargetScanner` already recognized the shape as an animation target — only the specific
"this is a play command" interpretation was thrown away, so `TimelineCompiler.compile()` ended up
with an empty timeline for that slide and legitimately (no exception, no warning) returned null,
falling the whole slide back to one static composite.

**Fix** (mirrors the Keynote architecture closely — same shared `LayerSpec.Media` model, same
click-gate design):
- `TimingModel.kt` gained `TimingBehavior.Command(verb: String)`; `TimingParser.kt` now parses
  `<p:cmd>` via `CTTLCommandBehavior` (already on the classpath, same `cBhvr`/`tgtEl` common
  structure every other behavior uses) instead of skipping it. The sibling `<p:video>`/`<p:audio>`
  media-declaration nodes (volume/mute/showWhenStopped metadata, not part of the click-triggered
  tree) stay skipped — not needed, the `<p:cmd>` alone carries everything required.
- `TimelineCompiler.synthesizeSpec()`: any `Command` behavior (verb doesn't matter — `playFrom`,
  `togglePause`, `pause`, `stop` are all non-visual) wins outright and produces
  `EffectSpec.Appear(EMPHASIS)` — exactly the choice already made for Keynote's `movie-start`
  build, for the same reason (poster already on screen, click shouldn't fade/reveal anything, only
  needs to exist as a timeline entry for `PresentationPlayer`'s `movieStepIndex` gating to find).
  Originally only special-cased `verb.startsWith("playFrom")`, which left the sample file's
  *second* interactive sequence (click the *playing* video to toggle pause — a real, separate
  `<p:cmd cmd="togglePause">` the file also has) falling through to the preset backstop and
  degrading to a spurious, misleadingly-worded "degraded to fade" warning; broadened to treat any
  `Command` uniformly once this showed up in `dumpTiming` output.
- `PptxLayerPlanner.plan()`: mirrors Keynote's `hasTopLevelMovie` bypass — a top-level
  `XSLFPictureShape` with `isVideoFile()` always gets its own `LayerSpec.Media` layer (checked
  ahead of paragraph/animated/background classification), and bypasses the
  `targets.isEmpty → return null` early-out, so a silent/no-behavior embedded video (one
  `AnimationTargetScanner` wouldn't otherwise flag) still becomes decodable rather than silently
  folding into the background band.
- `PowerPointDeckSupport.layerResolver()` gained a `mediaLayers` map alongside the existing
  `shapeLayers`/`paragraphLayers` — without it, a behavior targeting a shape whose only layer is
  `Media` resolved to `emptyList()` and was silently dropped.
- `PptxSlideRasterizer.rasterizeLayer()`'s `Media` case reuses the exact same `renderCropped(...)`
  call the `Shape` case already uses — POI's generic `DrawFactory` already paints a video shape's
  poster correctly, zero new drawing code needed. The only new logic resolves the actual video
  bytes: `getVideoFileLink()` → `slide.getRelationById(rId)` (POI has no typed relation for
  video, so this always resolves to a generic `POIXMLDocumentPart`) →
  `.getPackagePart().getInputStream()` → extracted to a temp file (extension preserved from the
  zip entry's own part name, since some decoders sniff container format from it), cached by
  relationship id. Since `PptxSlideRasterizer` is a stateless `object` with no lifecycle (unlike
  `KeynoteSceneRasterizer`, a `class` with `close()`), the temp-file cache map lives in
  `DeckRasterizer` instead (which already owns `slideShow`/`keynoteSceneRasterizer` lifecycle) and
  is passed in as a parameter — deleted in `DeckRasterizer.close()`, not just `deleteOnExit()`,
  for the same leak-prevention reason already documented on the Keynote side.
- App-side: **zero changes needed** beyond a rename. `PresentationPlayer.kt`'s
  `movieLayerId`/`movieStepIndex`/`ensureMovieDecoder`/`syncMovieTarget` machinery already operated
  generically on `LayerSpec.Media`, and `KeynoteMovieDecoder` (built for Keynote) was already
  format-agnostic (`videoFile: File`, `posterCanvas: BufferedImage`, `contentRectPx: Rectangle`,
  nothing Keynote-specific) — renamed to `EmbeddedVideoDecoder` since it now serves both formats,
  no other change.

**Verification**: `dumpTiming` before/after — before: the video slide fell back to one static
composite, zero warnings (the empty-timeline null-return is silent, not exceptional); after: a
real `Media` layer with two `Appear(EMPHASIS)` steps (`dur=24753` for `playFrom`, `dur=1` for
`togglePause`) and one honest pre-existing warning ("Shape-click trigger compiled as click step
(outputs are not interactive)" — the toggle-pause interactive sequence, correctly not treated as
anything more than a no-op click step, since clicking a specific point on a live output isn't
something this architecture supports for any content type). Full engine test suite green
throughout, no new tests added (the plan's own verification was `dumpTiming` + hands-on, matching
what was approved). Hands-on in the running GUI (System Events + screenshots): the video's poster
shows immediately in the slide's pre-click state (`Build 0 of 2`), does not play until the build
step is advanced (confirmed answering the original ask — playback must wait for the click, not
autoplay), and once advanced the frame visibly changes across repeated screenshots (real gymnast
footage progressing, not a static swap) — genuine ongoing decode, not a one-time frame grab.

**Files Modified**: `pptx/TimingModel.kt`, `pptx/TimingParser.kt`, `timeline/TimelineCompiler.kt`,
`pptx/PptxLayerPlanner.kt`, `pptx/PowerPointDeckSupport.kt`, `pptx/PptxSlideRasterizer.kt`,
`DeckRasterizer.kt` — all engine-side; `presenter/EmbeddedVideoDecoder.kt` (renamed from
`KeynoteMovieDecoder.kt`), `presenter/PresentationPlayer.kt` (updated references + comments) —
app-side.

## Presentation API (March 2026)

**Feature**: Mobile companion app can now retrieve presentation slides via the Ktor REST API.

### How it works
1. When slides finish loading in `PresentationTab`, the `onSlidesLoaded` callback fires with the raw `BufferedImage` list.
2. `MainDesktop` forwards this to `companionServer.updatePresentation(id, fileName, fileType, bufferedImages)`.
3. `CompanionServer` JPEG-encodes each slide on the IO thread and stores them in `_slideBytes: ConcurrentHashMap<String, List<ByteArray>>`.
4. The metadata is stored in `_presentationCatalog` and broadcast to WebSocket clients via `presentation_updated`.

### Background rendering (schedule items)
When `updateSchedule` is called with a `PresentationItem`, `CompanionServer` immediately:
1. Computes the stable file-hash ID and stores the mapping `scheduleUUID → fileHash` in `_scheduleItemToPresentationId`.
2. Launches a coroutine on `Dispatchers.IO` that renders the file through the shared
   presentation engine (`PresentationLoader` + `DeckRasterizer` — same pipeline as
   `PresentationViewModel`), reusing the shared `SlideDiskCache` when the tab already rendered
   the file and writing into it otherwise (July 2026: the old duplicated reflection renderers
   were deleted).
3. On completion the JPEG bytes go into `_slideBytes[fileHash]` and metadata into `_presentationCatalogs[fileHash]`.

`GET /api/presentations/{scheduleUUID}` resolves the UUID via the map and returns the `PresentationDto` immediately when rendering finishes. While still rendering, it returns `404` — the client should retry.

### New Endpoints
| Endpoint | Description |
|---|---|
| `GET /api/presentations` | Returns `PresentationCatalogResponse` with slide metadata |
| `GET /api/presentations/{id}` | Returns `PresentationDto` for a specific presentation by schedule UUID **or** file hash — slides rendered in background |
| `GET /api/presentations/{id}/slides/{index}` | Returns JPEG image bytes for slide at index (resolves schedule UUID automatically) |

### Presentation ID
Derived from `file.absolutePath.hashCode().toUInt().toString(16)` — stable per file path.

### New DTOs
- `SlideDto` — `slide-index` + `thumbnail-url`
- `PresentationDto` — `id`, `file-name`, `file-type`, `slide-total`, `slides[]`
- `PresentationCatalogResponse` — `presentations[]`, `total`

### New CompanionServer state
- `_presentationCatalogs: ConcurrentHashMap<String, PresentationDto>` — keyed by file hash; populated both by `updatePresentation` (tab-load) and `renderPresentationForServer` (background)
- `_scheduleItemToPresentationId: ConcurrentHashMap<String, String>` — maps schedule UUID → file hash
- `_renderingPresentations: ConcurrentHashMap<String, Unit>` — in-progress render guard (putIfAbsent pattern)

### New WS Event
`presentation_updated` — broadcast whenever a new presentation is loaded; also sent to newly-connected WS clients.

### `PresentationViewModel` change
`_bufferedSlides: MutableList<BufferedImage>` is now kept in sync alongside `_slides: SnapshotStateList<ImageBitmap>` for every slide source (PDF, PPTX, PPT, Keynote). Access via `viewModel.bufferedSlides`.

### Key rule
- Do **NOT** use `toAwtImage()` on `ImageBitmap` — this extension does not exist in this project. Always work with `BufferedImage` directly and convert to `ImageBitmap` via `bufferedImageToImageBitmap()`.

## Bible Follow Along — Tiered Auto-Follow (July 2026)

**Problem**: the BLE detection engine (`ChurchPresenter-BLE/`) reports five distinct match types —
`explicit`, `continuation`, `chapter-scan`, `chapter-history`, `reverse` — with real, documented
differences in false-positive risk (see the BLE module's own `TRAINING_PLAN.md` Known Engine Gaps
table). None of that distinction reached the app: `BibleViewModel.onEngineScripture()` collapsed
everything that wasn't literally `explicit`/`continuation` into one generic `REVERSE` bucket, and
whenever the "Auto-follow" toggle was on, *every* accepted detection instantly pushed to the output
screen with zero confirmation — an inferred chapter-history/reverse jump could put a wrong verse
live with no safety net.

**Fix**: no engine or transport change was needed — `Broadcaster.kt` already serializes the engine's
full `matchType` string over the WebSocket, and `BibleEngineClient.handleMessage()` already forwarded
it through unmodified; the app was just discarding the distinction.
- `DetectionSource` enum (`BibleViewModel.kt`) extended from 3 to 5 values, matching the engine's
  real match types 1:1.
- Auto-follow's go-live decision is now tiered by `matchType`: `explicit` (reference stated outright)
  and `continuation` (simply the next verse in the passage) still go live instantly — sequential
  next-verse reading is the expected default case, not a risky jump. `chapter-scan`/`chapter-history`/
  `reverse` (inferred matches with no reference actually spoken) now stage the browse view via
  `navigateToReference(..., goLive = false)` — the same mechanism a manual single-click on a
  detection chip already used — instead of pushing live. The operator's existing double-click
  accepts it, or a follow-up `continuation` detection confirms and goes live on its own.
- No new settings/config knob — the instant-vs-staged split is a fixed, documented rule based on
  match type, not a numeric threshold.
- `BibleTab.kt`'s detection-chip icon row gained two new markers (`CHAPTER_SCAN`, `CHAPTER_HISTORY`)
  so the richer match type is visible, not just inferred.

**Also fixed while here**:
- `TextMatchLevel`'s KDoc claimed it "gates only the text-match stage" — false; the engine's
  `Config.applyLevel()` also changes the confidence floor (`Stabilizer`, applies to *every* detection
  type) and the sticky book/chapter timeout. Comment corrected; the "Text match" pill also gained a
  tooltip (it had none, unlike the Auto-follow pill next to it).
- Extracted the "consume this go-live token exactly once" bookkeeping — previously hand-rolled
  identically in both `BibleTab.kt` and `MainDesktop.kt`'s tab-switch fallback — into
  `composables/TokenGate.kt` (`rememberTokenGate`). Behavior-preserving; removes a duplication that
  could drift (this exact pair of effects was already the site of one bug, commit `dcd5f9d`).
- **`matchType` now flows into the training logs.** Before this, `TrainingDataLogger.logLiveReference()`/
  `logSuggestionOutcome()` had no field recording *which* match type a live reference or suggestion
  outcome traced back to — so there'd have been no way to later ask "did staged chapter-history
  suggestions actually get accepted, or mostly ignored?" to validate whether the instant-vs-staged
  split above is calibrated right. Both functions gained an optional `matchType: String?` param
  (`DetectedReference.matchTypeLabel()` joins a chip's `sources` into the engine's own label strings).
  Threaded through every call site that traces back to a specific detection
  (`onEngineScripture`/`setAutoFollow`/`applyDetectedReference`/`logGoLiveCorrection`/
  `clearDetectedReferences`, plus a new `_autoFollowLiveMatchType` state alongside the existing
  `_autoFollowLiveSource` for the deferred go-live path); left `null` at every site with no detection
  context (free browsing, schedule clicks, remote Companion API go-live) — `selectVerseByDetails`
  explicitly resets it to `null` so a stale matchType from an earlier detection can't leak into an
  unrelated go-live.

**Files Modified**:
- `viewmodel/BibleViewModel.kt` — `DetectionSource` enum, `onEngineScripture()` matchType mapping +
  tiered go-live call, `TextMatchLevel` KDoc, `matchTypeLabel()` helper, `_autoFollowLiveMatchType`,
  `matchType` threaded through `navigateToReference`/`setAutoFollow`/`applyDetectedReference`/
  `logGoLiveCorrection`/`clearDetectedReferences`/`selectVerseByDetails`
- `tabs/BibleTab.kt` — new icon/tooltip branches, Text-match tooltip, `TokenGate` adoption,
  `goLiveWithHistory()` gained a `matchType` param
- `MainDesktop.kt` — `TokenGate` adoption, `matchType` passed at the tab-switch fallback go-live
- `composables/TokenGate.kt` — new
- `utils/TrainingDataLogger.kt` — `matchType: String? = null` param on `logLiveReference()` and
  `logSuggestionOutcome()`
- `composeResources/values/strings.xml` — `bible_stt_src_chapter_scan`, `bible_stt_src_chapter_history`,
  `bible_stt_text_match_hint`

**Build Status**: ✅ Compiles successfully (`gradlew compileKotlinJvm`, zero warnings). Not yet
verified against a live STT session — the tiering logic was traced by hand against the exact
existing wiring (`navigateToReference`'s `goLive` param, `_autoFollowLiveToken` vs
`_verseSelectionToken`) rather than run end-to-end.

## BLE Engine Improvements (July 2026)

**Scope**: the Bible Lookup Engine submodule (`ChurchPresenter-BLE/`) + its app integration.
Grounded in a real recorded session (`~/Downloads/ble/2026-07-08_183702.db`, ~1.5 h Russian
service + matching engine/operator logs).

**Engine (submodule commits a81bb7a, 6dca169, d492197, 7b41dce)**:
- **Replay harness** (the docs promised one that never existed): `DetectionEngine` takes an
  injectable clock (threaded into Stabilizer/ContinuationEngine), so `DbReplayTest` replays a
  recorded STT `.db` deterministically against a committed golden JSONL (refs/scores only —
  privacy-safe). `replayEval` gradle task scores a replay per matchType against the operator's
  live-references/suggestion-outcomes (±90 s window). STT payload parsing + the
  last-2-segments window rule moved to `SttPayload.kt`, shared by live socket and replay.
  Regenerate goldens ONLY with an intentional behavior change: `-Dreplay.updateGolden=true`,
  summarize the diff in the commit.
- **FP fixes** (the Known Engine Gaps rows, now marked resolved): over-extension gate (token
  ≥3 chars past its matched stem needs digit/marker corroboration) + short-alias gate
  (single-token exact aliases ≤4 chars need the same). Replay diff on the real session:
  125→116 events, all removals verified prose FPs, zero TP loss. `buildRefEvent` fails closed
  (no fabricated verse 1, no first-verse substitution — it auto-goes-live at 0.95).
  `pickTranslation` matches the citing track's dominant script against each bible's
  content-derived `Script` instead of hardcoded ids (which were live-broken: filename-derived
  language junk sent KJV text for Russian citations).
- **Concurrency**: all detection-state mutation confined to one named dispatcher; Broadcaster
  gives each WS client a bounded drop-oldest channel (slow clients can't stall STT ingest —
  removed both `runBlocking` sites); utterances map LRU-bounded; new additive `engine_status`
  message (`sttConnected`/`sttConfigured`) broadcast on STT transitions + replayed to late
  joiners. Golden byte-identical → behavior-preserving.
- **Hygiene**: legacy `ExplicitParser` retired (tests ported to ReferenceWatcher); README/
  TRAINING_PLAN made truthful (replay section real, dangling REFERENCE_DETECTION_PLAN refs
  gone, gap table updated incl. a NEW gap found while testing: EN keyword-after-number
  citations "Job chapter 3 verse 2" mis-parse chapter/verse; RU unaffected); DetectionLogger
  disk writes moved to a background thread; BibleIndex sized from actual load.

**App side (main repo)**:
- `BibleEngineClient` handles `engine_status` → `engineSttConnected: State<Boolean?>` (null =
  older engine → BibleTab keeps its proxy inference); BibleTab shows the error-tinted
  `bible_stt_engine_stt_down` status when the engine is reachable but ITS STT socket is down
  (previously the UI said "Listening" off the app's own separate STT connection). Reconnect
  loop gained exponential backoff (2 s floor, 30 s cap, jitter).
- **Staged-suggestion outcomes**: detection chips that scroll off unclicked log `"ignored"`;
  `clearDetectedReferences(reason)` labels un-acted chips `"dismissed"` (operator) or
  `"expired"` (engine stop); accepted/corrected chips aren't double-labeled. Every staged
  suggestion now produces exactly one suggestion-outcomes row → acceptance-by-tier (the open
  question from the July tiering work) is computable from that log alone.

**Recall/noise round (July 2026, submodule commits 9c16216, 55b683e, 9dc70f3, 67f96e1)**:
- Sequential continuation scores VERSE-side coverage (`Config.continuationMinCoverage`) — the
  old query-normalized overlap was diluted by the 2-segment window and missed verbatim
  verse-by-verse reading.
- Keyword-first citations bind correctly ("глава 26 стих 3" parsed inverted before; the EN
  translation track was emitting wrong 0.95 explicits like Matthew 9:9 / Genesis 24:2).
- chapter-history spam cut 94 → ~2 emissions with structural gates: candidate pool = sticky +
  5 most-recent chapters, verse-coverage floors (0.45 scan / 0.6 history), agreement 0.20 —
  values documented as provisional in Config.
- Reverse lookup: fuzzy stem expansion for garbled STT tokens ("туждающие"→"труждающиеся" —
  1 edit on stems, 3 raw), per-track windows (the concatenated query's tail-25 cut Russian out
  whenever translation was active), different-chapter ambiguity competitor, passage-start
  backtracking (a straddled window favors the newest verse; step back while the previous verse
  is covered).

**Final eval on the recorded 2026-07-08 session: 7/7 ground-truth references detected (was
4/7 before this work began), total emissions 31 (was 125), zero labeled reverse FPs.**

**Verification**: engine suite green (145+ tests incl. golden replay + determinism guard);
app compiles zero-warning. Not verified live: an end-to-end STT session through the patched
engine (needs the STT server) — the replay harness is the regression net until then.

## Lower Third Disappearing Mid-Playback (July 2026)

**Problem**: a Lottie lower-third animation, triggered via the plain in-app "Go Live" button,
would start playing and then vanish partway through — reproduced on both Windows and macOS,
whole graphic gone at once (not just masked text layers), same point every time for a given clip.

**Investigation** (see full history in git log around commits `f5ea47fe`..`ad9c5405` if useful):
1. First hypothesis — a native/GPU memory leak in `LowerThirdPresenter.kt`'s raw-frame playback
   path (`intArrayToImageBitmap()` created 4 unclosed native Skia objects per frame, up to 30fps).
   This was real and worth fixing (see below) but **did not** resolve the reported bug.
2. Added temporary diagnostic logging (state machine timing, composable-level `composition`/
   `rawFrames` heartbeats, `requestClearDisplay()`/`setPresentingMode()` callers) and had the user
   reproduce on both platforms while confirming the bug occurred in that exact run. The logs came
   back **completely clean** every time: correct timing, no early clear, no exception, no stale
   composable inputs, right through a normal end-of-clip teardown — twice, on both platforms.
3. This proved the bug lives inside Compottie's own live/vector renderer (`rememberLottiePainter`,
   used whenever pre-rendered raw frames aren't ready yet) — a silent rendering failure inside
   third-party library code, invisible to any of our own state.

**Fix**: rather than chase a bug inside third-party rendering internals, stop relying on that
renderer for longer than necessary. `main.kt`'s central Lottie `LaunchedEffect` was rewritten from
"commit to whichever path (live vs pre-rendered) was ready at effect-start" to a single elapsed-time
loop that polls `presenterManager.lottieRawFrames` live on every tick and switches over to the
pre-rendered raw-frame path — continuing from the same elapsed-time position, no visible jump — the
instant it's ready, since observed pre-render times (2.5–5.6s) are well within typical clip lengths.
Confirmed fixed by the user on both platforms after this change.

**Also fixed along the way**:
- `LowerThirdPresenter.kt`'s raw-frame path now explicitly closes its native Skia `Bitmap` via
  `RememberObserver` the instant a frame is superseded (previously leaked ~8MB/frame, up to 30fps,
  relying only on GC/Cleaner timing), and skips a wasteful `Image`+`Canvas` round-trip by calling
  `Bitmap.asComposeImageBitmap()` directly instead of `SkiaImage.makeFromBitmap(...).toComposeImageBitmap()`.
- `PresenterManager.kt`'s pre-render step now discards a pre-render that comes back (near-)blank
  (`isFrameBlank()`, samples a few frames for near-zero alpha) instead of publishing it, falling
  back to live rendering the same way an actual render failure already did.

**Files Modified**: `presenter/LowerThirdPresenter.kt`, `main.kt` (central Lottie effect),
`viewmodel/PresenterManager.kt`. All temporary diagnostic logging (a `LowerThirdDebugLog` utility,
composable-level heartbeats, caller-tracking on `requestClearDisplay()`) was added, used to
diagnose, and then fully removed once the fix was confirmed — per the debugging guidelines below.

**Build Status**: ✅ Compiles successfully. Confirmed fixed by the user on both Windows and macOS.

## Unified Lottie Render Pipeline (July 2026)

**Problem**: lower thirds were choppy (fixed 33 ms `delay()` playback clock + an ~8 MB
per-frame `installPixels` on the composition thread **per output window**), had blocky
letters (no TTFs anywhere in the repo and no `fontManager` passed to Compottie, so text fell
back to a default typeface; plus the in-RAM pre-render degraded long clips to 720p/15fps under
a 1.2 GB budget and upscaled), and the same lottie was fully rendered **twice** — once into
`List<IntArray>` RAM (~750 MB for 3 s @1080p30) for desktop, once into a YUVA `.acpc` disk
cache for ATEM.

**Architecture now** (one render pass, shared by everything):
- **`server/LottieRenderCache.kt`** — replaces `AtemRenderCache` (deleted). Disk cache at
  `~/.churchpresenter/lottie_render_cache/*.lrcc` of raw **RLE-compressed ARGB frames**
  (format documented in the file header; footer offset table gives random access). RLE runs
  over whole 32-bit pixels — the `0xFEFE…` sentinel trick used for YUVA is NOT safe in ARGB.
  Mostly-transparent lower thirds shrink >99% (verified: 330-frame 11 s 1080p clip = 5.4 MB
  vs 2.7 GB raw). Variant policy: desktop and ATEM share one entry when the ATEM raster has
  the same aspect (per-axis max of clamped lottie canvas and raster; default 1920×1080@30
  for both). ATEM uploads convert ARGB → YUVA+RLE **at upload time** via
  `Reader.nextAtemFrame(rasterW, rasterH)` (bilinear scale only when sizes differ);
  `AtemFrameEncoder` unchanged. Startup warm-up (`ensureForFolder`, main.kt) now also runs
  with no ATEM configured, so playback caches are ready before first Go Live.
- **`presenter/LottieFrameStream.kt`** — per-play streaming decoder owned by
  `PresenterManager`. One worker decodes the requested frame index off the UI thread into a
  Skia bitmap **once for all windows** and publishes `PresenterManager.lottieFrame`
  (a `LottieFrame`); superseded bitmaps are closed after 3 newer publishes. RAM is a few
  frames flat regardless of clip length — the degradation ladder is gone, clips always render
  at full resolution. Blank-render guard: samples 4 decoded frames; a blank cache entry is
  deleted (so it re-renders next time) and playback stays on the live Compottie fallback.
- **Playback clock** (`main.kt` central Lottie `LaunchedEffect`): `withFrameNanos`-driven —
  elapsed time from real frame timestamps, so missed frames self-correct instead of drifting.
  Polls `lottieFrameCount` live each tick (raw-vs-live switchover mid-play preserved). The
  KEY-role output and the LivePreview/BrowserSource presenters now receive `frame` too
  (previously they silently froze once raw frames took over — `lottieProgress` stops updating).
- **Fonts** (`utils/LottieFonts.kt` + `resources/fonts/*.ttf`): a `LottieFontManager` passed
  at **every** `rememberLottiePainter` call site (presenter, offscreen renderer, tab preview,
  settings preview). Resolves the 11 LottieGen families from bundled TTFs (which also fixes
  LottieGen's own AWT text measurement — same classpath), then falls back to installed system
  fonts found by filename in platform font dirs (covers e.g. Verdana). ⚠️ When adding a painter
  call site that can draw text, pass `fontManager = LottieFonts` or text renders wrong.
- **LottieGen glyph embedding** (`lottie/GlyphExtractor.kt` in the submodule): exports now
  embed vector outlines (`chars`) for the characters actually used, so files render crisp text
  in ANY player with no fonts installed (verified in lottie-web/Safari). In-app rendering still
  prefers the resolved real font. Limitation: runtime search-replace text substitution can
  introduce characters that weren't embedded — external players then miss those glyphs;
  in-app rendering is unaffected (real fonts).

**Key invariants**:
- Cache pixels are canonical ARGB ints; the decode path installs them little-endian
  (= BGRA bytes = Skia N32).
- ATEM media MUST be uploaded at the switcher raster (`AtemSettings.renderWidth/Height`) —
  any other size gives a stride/chroma-shifted image. The cache may store a larger same-aspect
  size; `nextAtemFrame` scales down at upload.
- Bump `LottieRenderCache.VERSION` when rendered pixels or the file format change — the
  version is part of the filename, old entries age out via LRU (4 GB / 60 entries).

**Verified**: RLE codec fuzz-tested (308 round-trip cases); real 330-frame render validated
on disk byte-for-byte (offset chain, per-frame decode, alpha coverage); cached mid-frame
decoded to PNG shows crisp full-res Verdana; glyph-embedded export renders correctly in
lottie-web. Not yet verified: an actual ATEM upload (no device configured on the dev machine)
— the YUVA encoder is unchanged, only its input source moved.

**Build Status**: ✅ Compiles successfully, zero warnings.

## Browser Source Output Overhaul (July 2026)

**Problem**: the Browser Source path (offscreen `ImageComposeScene` → pixel-diff dirty-rect →
image encode → WebSocket → overlay-page canvas) lagged the real output windows: MEDIA and
WEBSITE had no render branch at all (stream went black; settings UI force-disabled those
checkboxes), "no background"/Transparent rendered solid black (no OBS overlay keying),
mode switches hard-cut instead of crossfading, resolution/fps were hardcoded 1080p/30, and
the WS handler had a send race (frame + heartbeat jobs on one session, unsynchronized
`lastFrame`).

**What changed** (all in `presenter/BrowserSourceVideoRenderer.kt`, `server/CompanionServer.kt`,
`PresenterScreen.kt`, `presenter/BiblePresenter.kt`, `presenter/SongPresenter.kt`,
`data/settings/ScreenAssignment.kt`, `dialogs/tabs/ProjectionSettingsTab.kt`, `main.kt`):
- **True alpha transparency**: new `presenter/LocalTransparentBlanking.kt` CompositionLocal,
  provided `true` inside the browser-source scene. `PresenterScreen` and BiblePresenter's /
  SongPresenter's own background layers consult it — "no background" / `BACKGROUND_TRANSPARENT`
  emit nothing (alpha 0) there instead of black; projector windows still paint black.
  ⚠️ Two coupled fixes shipped with this — do not revert independently: the overlay JS now
  does `clearRect` before drawing a *partial* delta (source-over blending of alpha deltas
  ghosts fade-outs), and `registerBrowserSourceFrames` closes an output's connected WS
  sessions when its flow is replaced (a session holds the flow captured at connect; after
  renderer restart the old flow never emits and the heartbeat re-sends a stale frame forever).
- **MEDIA**: renderer takes `mediaViewModel` and provides `LocalMediaViewModel` in the scene;
  video draws muted via `MediaPresenter(audioEnabled = false)` — frames come from the single
  master player's `SharedVideoOutput` (no second VLC pipeline), audio stays on the main output.
  Audio-only files: background only (same as real windows).
- **WEBSITE**: mirrors `presenterManager.webSnapshot` (same as LivePreviewPanel) — only live
  while the Web tab or a real output window hosts the JCEF browser; documented in the
  settings-UI tooltip (`browser_source_website_snapshot_tooltip`).
- **Per-output resolution + fps**: `ScreenAssignment.browserSourceWidth/Height/Fps`
  (defaults 1920/1080/30 keep old settings.json loading); renderer `remember`-keyed on them
  in main.kt so a change rebuilds it and clients reconnect at the new geometry; dropdowns in
  Projection settings (720p–4K, 10–60 fps).
- **Mode crossfade**: same `Crossfade(effectiveMode, tween/snap)` + duration formula as
  main.kt's real windows; visibility gates moved inside each `when (mode)` branch so the
  outgoing mode fades instead of vanishing.
- **JPEG fast path**: `encodeFrame` scans the rect for any non-opaque pixel (early exit);
  fully opaque → JPEG (several times faster/smaller — what makes continuously-changing MEDIA
  sustainable), any alpha → PNG. Client sniffs the first payload byte (0x89 PNG / 0xFF JPEG).
- WS sends are Mutex-serialized; `lastFrame` is an `AtomicReference`; jobs run in the session
  scope.

**Verified live** (macOS, real app + WS probe + Safari): first full frame at 1920×1080 is
100% transparent alpha; lower-third playback streams tight dirty-rect deltas (e.g. 428×132
at the graphic's position) as PNG with anti-aliased alpha edges; overlay page composites
over the page background with no ghosting. Not yet verified live: JPEG path with an opaque
background, resolution-change reconnect, MEDIA/WEBSITE/crossfade in OBS — logic traced,
needs a hands-on pass.

## InstanceLink Overhaul (July 2026)

**Problem**: the follower↔primary link had no heartbeat (a dead link showed CONNECTED with a
frozen screen for many minutes), a fixed 2 s reconnect loop, caches that were `exists()`-gated
forever (bible/pictures/backgrounds never refreshed), no dedup on `live_state_changed`
broadcasts, mode-only mirroring for MEDIA/CANVAS/QA/DICTIONARY, and fire-and-forget controller
commands that were silently dropped.

**What changed**:
- **Heartbeat both sides**: server `install(WebSockets) { pingPeriodMillis = 10_000;
  timeoutMillis = 20_000 }` (protocol-transparent to mobile clients; also clears ghost
  follower entries) + client `pingIntervalMillis`/session `timeoutMillis`. Verified live: a
  silent client is closed after exactly 30 s.
- **Reconnect**: exponential backoff 1 s→30 s cap ±20% jitter; `reconnectDelayMs` setting is
  now the FLOOR, not a fixed cadence. On dropout the follower keeps showing last content
  (never blanks the audience) while the badge shows "Link lost — reconnecting in Xs" and the
  dialog shows "Last update Xs ago" (`lastMessageAtMs`/`nextRetryAtMs` flows on
  `InstanceLinkViewModel`). Toggling the link off now actually disconnects; the silent
  auto-arm of `enabled/autoConnect` on first success was removed (the dialog persists it
  explicitly).
- **Freshness**: `updateLiveState` dedups byte-identical DTOs (verified live: two identical
  re-sends → zero extra broadcasts); new `backgrounds_updated` broadcast + the
  never-actually-sent `secondary_bible_updated` now fires; broadcast buffer 16→64. The
  follower handles `bible_updated`/`secondary_bible_updated`/`pictures_updated`/
  `backgrounds_updated` by invalidating the corresponding instance-link cache — and since the
  connect snapshot re-sends bible/pictures events, every (re)connection deliberately refreshes
  once. Live-state/slide collectors use `collectLatest` + temp-file-rename cache writes so a
  cancelled apply can't strand a truncated cache file.
- **Content types**: `applyRemoteLiveState` now mirrors MEDIA (streamed from the primary via
  `/api/media/stream/{id}`, muted-equivalent — no position/transport sync, DTO carries none),
  CANVAS (id-match against local scenes, hoisted from MainDesktop via `onScenesChanged` —
  content is NOT fetchable, both instances need the same scenes.json), QA (minimal Question),
  and DICTIONARY (full `StrongsEntry` carried in `LiveStateDto.dictionaryEntry` — the
  follower's own dictionary may be a different language, so no local lookup). STT stays
  mode-only (`mode_only_no_feed`) — no caption feed exists.
- **Command acks**: `WebSocketMessage` gained `commandId` with `@EncodeDefault(NEVER)`
  (**critical**: the server json has `encodeDefaults=true`, so without NEVER every existing
  broadcast would grow a `"commandId":null` field and change the wire format for all clients —
  verified byte-identical via probe). Primary replies `command_ack` (instant commands: ok;
  approval-gated: ok + `pending_approval` immediately; unknown: `unknown_command`). Follower
  awaits acks with a 5 s timeout; failures surface via `InstanceLinkViewModel.commandFailures`
  → `dialogs/InstanceLinkToast.kt`; a never-acking (older) primary triggers ONE soft notice
  per connection. Legacy raw `{"ok":...}` decision replies kept for mobile compatibility.
- **Testing enabler**: the single-instance lock port can be overridden —
  `JAVA_TOOL_OPTIONS="-Dchurchpresenter.singleInstancePort=47633 -Duser.home=$HOME/cp-follower"`
  runs an isolated second instance on one machine.

**Verified live** (real app + python WS probe): connect snapshot order; ack round-trip with
matching commandId; unknown-command ack; no-commandId compat (no ack, no error);
live-state dedup (0 extra broadcasts for identical re-sends); envelope byte-purity (no
undecodable frames); silent-client disconnect at exactly 30 s with clean follower logs.
**Not verified live**: full two-GUI-instance drills (reconnect countdown badge, cache
re-download on bible/picture change, MEDIA/CANVAS/QA/DICTIONARY rendering on a real follower,
failure toast) — logic traced and compile-checked; needs a hands-on pass with two instances.

## Presentation Playback Fixes: Bullet Merging, Reverse Navigation, Video Autoplay (July 2026)

**Problem**: hands-on testing of the animated Presentation Engine surfaced three bugs on the same
`RandomPresentation.key`/`.pptx` pair used throughout the animation work: (1) Keynote text boxes
with multiple bullets rendered as one run-on sentence; (2) navigating backward always landed on
"build 0" instead of the destination slide's last-completed build; (3) embedded videos (both
formats) started playing immediately on slide entry instead of waiting for their click/build step.

**Bug 1 root cause**: `KeynoteDeckParser.parseParagraphs()` split text on `\n` after replacing only
` `. Confirmed via `dumpKeynote` (extended to print the untruncated joined text plus a
non-ASCII code-point list — the tool's own preview was truncated to 30 chars, which had been
hiding the evidence) that real bulleted text boxes use a lone CR (`\r`, U+000D) between bullets,
never ` ` — so `text.split('\n')` never found a boundary and all bullets landed in one
`KnParagraph`. Fixed by also replacing `\r`→`\n` (both single-character swaps, so
`parseParagraphs`'s running char offset into the attribute-run tables stays exact — a
length-changing `"\r\n"→"\n"` substitution would have desynced it for any later paragraph).
Applied to both `parseParagraphs()` and the sibling presenter-notes text extraction via a shared
`normalizeParagraphBreaks()` helper. Verified with `dumpTiming`'s PNG render: three bullets that
were one line are now three.

**Bug 2 root cause**: `PresentationPlayer.showSlide(index)` unconditionally set `stepIndex = -1`
regardless of navigation direction — real PowerPoint/Keynote show a slide you step *back* onto
fully built (as the audience last saw it), only a slide you step *forward* onto starts unbuilt.
Fixed by adding `showSlide(index, enterAtLastStep: Boolean)`: when true and the destination slide
is already cached (the common case — `evictBeyondWindow` keeps `current-1..current+1`), enters at
`stepCount - 1` synchronously; otherwise defers via `pendingEnterAtLastStepFor`, applied once
`ensureLoaded()`'s coroutine populates the cache (guarded against a stale request if the operator
navigates elsewhere first). Threaded through `PresenterManager.presentationShowSlide()` and a new
one-shot `PresentationViewModel.consumeEnteredViaPreviousSlide()` flag set only by
`previousSlide()` (cleared by `nextSlide()`/`selectSlide()`) — scoped narrowly so only genuine
backward navigation (arrow/PageUp, clicker, Instance Link "send previous") gets the new behavior;
forward navigation and thumbnail clicks are unaffected.

**Bug 3 root cause**: not in `PresentationPlayer`'s step-gating math (traced by hand against the
real file's video build — top-level drawable, its own click step, ids matching end to end — the
gating logic is correct on paper). The actual bug is a race in `EmbeddedVideoDecoder.start()`:
`player.media().play(...)` only queues the open/play command (libvlc transitions to actually
playing asynchronously), so the synchronous `player.controls().pause()` right after it can land
while the player is still "opening" and silently no-op. That would be self-correcting — `frame()`
calls `pause()` again every subsequent animation frame — except `pause()`/`resume()` were guarded
by a `resumed` flag that starts `false`, so *every* pause() call before the operator's first real
click was ALSO a no-op (the guard meant for cheap idempotence instead prevented the race from ever
being corrected). Fixed by removing the racy inline `pause()` from `start()` and making
`resume()`/`pause()` issue the real libvlc command unconditionally on every call (documented as a
cheap no-op when already in that state, which is why calling every frame was already the design)
— `resumed` now only gates the one-time volume change. Shared by both formats
(`EmbeddedVideoDecoder` serves Keynote and PPTX alike), matching that the bug was reported on both.

**Verification**: engine `./gradlew test` and app `./gradlew compileKotlinJvm` green after each
fix. Bug 1 additionally verified visually via a `dumpTiming` PNG render. Bugs 2 and 3 could not be
verified hands-on this session — the dev machine's screen locked (idle timeout) mid-verification
and automation couldn't unlock it; needs a hands-on pass (System Events + screenshots, or just the
user's own testing) to confirm the built-out-of-cache and video-timing behavior on a live run.

**Files Modified**: `KeynoteDeckParser.kt` (`normalizeParagraphBreaks`), `tools/DumpKeynote.kt`
(untruncated text preview, chunk count, non-ASCII code-point dump — kept as a lasting improvement
to the debug tool, not reverted) — engine-side; `presenter/PresentationPlayer.kt`
(`enterAtLastStep`, `pendingEnterAtLastStepFor`), `viewmodel/PresenterManager.kt`
(`presentationShowSlide` parameter), `viewmodel/PresentationViewModel.kt`
(`consumeEnteredViaPreviousSlide`), `tabs/PresentationTab.kt` (consumes the flag),
`presenter/EmbeddedVideoDecoder.kt` (`start`/`resume`/`pause`) — app-side.

### Follow-up: choppy playback (same day)
The fix above's `resume()`/`pause()` — reissuing the native `play()`/`pause()` command
unconditionally on every call — is itself called every evaluated display frame by
`PresentationPlayer.frame()` for the *entire* duration of playback, not just at the click
transition. The user reported choppy video (both formats) immediately after this shipped;
hammering libvlc's play/pause at display refresh rate for the whole clip turned out not to be
free despite the "documented no-op" assumption. Fixed by tracking the *confirmed* state via
vlcj's own `MediaPlayerEventListener.playing()`/`paused()` callbacks (`confirmedPlaying`/
`confirmedPaused`) — `resume()`/`pause()` now only reissue the native command while unconfirmed,
closing the original async-open race (still retries every ~16ms until the event lands) without
spamming libvlc once steady-state is confirmed. Verified hands-on: smooth playback on both
`RandomPresentation.key` and `.pptx`'s video slide, autoplay-gating still holds. Do not revert to
a blind per-frame reissue — confirm the state via the player events instead.

## PPTX Custom-Path Entrance Flinging Text Off-Screen (July 2026)

**Problem**: on `RandomPresentation.pptx`, slide 5's bullet text flew off-screen when its entrance
build played, even though the render used for thumbnails/disk cache looked correct — a live-vs-
static-render discrepancy, since thumbnails render via POI's plain `slide.draw()`
(`DeckRasterizer.renderFinalFrame`), completely bypassing the animated layer/timeline path that
`PresentationPlayer` actually uses live.

**Root cause**, found by unzipping the real slide XML rather than guessing: the shape's entrance
is PowerPoint's "Spiral" preset (`presetClass="entr" presetId="15" presetSubtype="9"`), which
already has a safe, correct mapping in `timeline/PresetCatalog.kt` (`zoomIn()`). But
`TimelineCompiler`'s behavior-first synthesis runs *before* the preset-id backstop and found
"usable" `ppt_x`/`ppt_y` `AnimateValue` curves, so it never reached the catalog. Those curves
were bogus: PowerPoint's real animation specifies the position via a `fmla` formula (spiral motion
using `cos`/`sin`/`$`-progress, decaying to exactly the shape's own resting position — engine
doesn't evaluate these) with a plain `<p:val>` fallback of literal `0`/`1` alongside it, meant only
as a placeholder for non-formula-aware viewers. `TimingParser.parseKeyframes()` was reading that
placeholder as if it were a real `MotionExpr` position — `0` and `1` are valid normalized
slide-relative positions ("left edge"/"right edge" of the *entire slide*, not the shape), so the
shape's held end-state landed offset by (+960pt, +510pt) — almost exactly half the slide's
1920×1080pt canvas — confirmed via `dumpTiming`'s curve dump (extended to print full keyframe
values, not just which properties are animated) showing `TRANSLATE_X=[(0.0,-960.0),(1.0,960.0)]`.

**Fix**: `TimingParser.parseKeyframes()` now returns empty (not the misleading placeholder values)
whenever any `<p:tav>` in the list has `isSetFmla` set — the plain `val` fallback is never
trustworthy when a real formula is present, and interpolating a mix of formula-backed and plain
keyframes doesn't make sense anyway. With no usable translate/scale/rotate curves,
`synthesizeSpec()`'s existing fallback chain correctly reaches
`PresetCatalog.fromPreset("entr", 15, 9)` → the already-present, correct Spiral→Zoom mapping.
`ppt_w`/`ppt_h` (already-ignored "Unhandled anim attribute" warnings, unrelated to this bug — those
attributes aren't fmla-driven here, just not yet mapped to scale curves at all) are unaffected.

**Verification**: `dumpTiming` before/after confirms the curve is gone and `Zoom(role=ENTRANCE,
fromScale=0.0)` is synthesized instead (5 honest degrade warnings now, up from 3 — `ppt_x`/`ppt_y`
correctly join `ppt_w`/`ppt_h` as "unhandled," which is truthful, not a regression). Full engine
`./gradlew test` green. Hands-on verified: Go Live on slide 5, advance the build — bullet text
settles fully on-screen instead of flying off to the bottom-right.

**Files Modified**: `pptx/TimingParser.kt` (`parseKeyframes`), `tools/DumpTiming.kt` (curve
keyframe values + layer `boundsPt` added to the diagnostic printout — kept as a lasting
improvement, not reverted) — engine-side only, no app changes needed.

## Known Issues

**Issue**: Song edits not saving/updating
- **Symptom**: Old and new song in logs show identical values
- **Added logs in**:
  - `EditSongDialog.kt` (save button click)
  - `SongsTab.kt` (onSave callback)
  - `SongsViewModel.kt` (updateSong method)
  - `Songs.kt` (saveSongToFile method)

**Next Steps**:
1. Run app and edit a song
2. Check logs to see where the values diverge
3. Once fixed, document solution here
4. Only then remove logs

## Architecture Cheat Sheet

### Package Responsibilities
All source under `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/`:

| Package          | Owns                                                                |
|------------------|---------------------------------------------------------------------|
| `tabs/`          | UI only — one file per tab, no logic                                |
| `viewmodel/`     | State + business logic; owns its own ViewModel, never passed around |
| `presenter/`     | Output window rendering (what the audience sees)                    |
| `server/`        | Ktor REST/WebSocket server, ATEM client, tunnel, SSL                |
| `data/`          | File I/O, database, song parsing, Bible data                        |
| `data/settings/` | Data classes for all persisted settings                             |
| `models/`        | Shared data models (ScheduleItem, SceneModels, etc.)                |
| `composables/`   | Reusable UI components (VideoPlayer, SceneCanvas, etc.)             |
| `dialogs/`       | All dialogs and settings dialog tabs                                |
| `utils/`         | Stateless helpers (AutoFit, UpdateChecker, CrashReporter, etc.)     |
| `ui/theme/`      | Theme, language provider, Material 3 wrappers                       |

### App Startup Chain
```
main.kt → MainDesktop.kt → tabs/* + PresenterManager → presenter/*
                        ↘ CompanionServer (server/)
                        ↘ StageMonitorScreen.kt
```
- `MainDesktop.kt` is the root composable — wires all tabs, ViewModels, and server together
- `presenter/Presenting.kt` — enum of what content type is currently live

### String Resources
- **Add new strings here**: `composeApp/src/jvmMain/composeResources/values/strings.xml`
- Locale files live in `values-ru/`, `values-uk/`, `values-pl/`, etc. — **never edit these** (see rule 2a)
- Access in code: `Res.string.your_key_name`

### Common Build Commands
```bash
./gradlew :composeApp:run          # run the app
./gradlew :composeApp:build        # full build + tests
./gradlew compileKotlinJvm         # compile check only (fast)
```

## Document Purpose

This is an **AGENT-ONLY** document. It helps me (the AI agent):
- Remember coding standards across sessions
- Track ongoing debug sessions
- Avoid repeating mistakes
- Document solutions for similar future issues

The user should not need to read this document.

