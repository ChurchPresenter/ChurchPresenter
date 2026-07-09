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

### Apache PDFBox (for Presentation Tab)
- 📦 **Dependency**: `org.apache.pdfbox:pdfbox:2.0.30`
- 📌 **Location**: `composeApp/build.gradle.kts`
- ⚠️ **Important**: After adding this dependency, you MUST run Gradle sync:
  - Command line: `./gradlew build`
  - IntelliJ: Click "Load Gradle Changes" or "Sync Now"
- 🔍 **Symptom if missing**: PDF slides won't load, console shows "PDFBox library not found!"
- ✅ **Solution**: Run Gradle sync to download the dependency

### Apache POI (for PowerPoint Slide Extraction)
- 📦 **Dependencies**: 
  - `org.apache.poi:poi:5.2.5` - Core POI library
  - `org.apache.poi:poi-ooxml:5.2.5` - For PPTX (PowerPoint 2007+) files
  - `org.apache.poi:poi-scratchpad:5.2.5` - For PPT (PowerPoint 97-2003) files
- 📌 **Location**: `composeApp/build.gradle.kts`
- ⚠️ **Important**: After adding these dependencies, you MUST run Gradle sync:
  - Command line: `./gradlew --refresh-dependencies build`
  - IntelliJ: Click "Load Gradle Changes" or "Sync Now"
- 🔍 **Symptom if missing**: PowerPoint slides won't load, console shows "Apache POI library not found!"
- ✅ **Solution**: Run Gradle sync to download the dependencies
- 📝 **Implementation Notes**:
  - Uses XSLF (XML SlideShow) for `.pptx` files
  - Uses HSLF (Horrible SlideShow Format) for `.ppt` files
  - Renders slides to BufferedImage using Graphics2D
  - Applies high-quality rendering hints (antialiasing, bicubic interpolation)
  - Converts BufferedImage to Compose ImageBitmap via Skia

### Keynote Slide Extraction
- 📝 **Challenge**: Keynote files (.key) don't have a standard API for slide extraction
- 🔍 **Solution**: Detect file format and unzip if compressed
- 📂 **File Format Detection**:
  - **Directory Package**: If `file.isDirectory` is true, access directly
  - **Compressed File**: If `file.isDirectory` is false, unzip to temp directory first
- 🗜️ **Unzip Process**:
  - Use macOS `unzip` command to extract .key file to temporary directory
  - Access extracted contents as regular directory
  - Clean up temp directory after loading slides
### Keynote Slide Loading Strategy (CROSS-PLATFORM, NO EXTERNAL APPS)
- ⚠️ **NEVER use osascript, AppleScript, qlmanage, sips, or any external process** — everything must run within the JVM
- ⚠️ **NEVER use shell `unzip` command** — use `java.util.zip.ZipInputStream` (pure Java, works everywhere)
- **How it works**: Unzip `.key` file with `ZipInputStream` → extract `st-` (slide thumbnails) + full-size `mt-` (image-only slides) from `Data/` → sort by `Index/Presentation.iwa` varint scan
- **st- files**: slide thumbnails for text/mixed slides — always present
- **mt- files**: media assets — full-size (no `-small-` suffix) = image-only slide content
- **Slide order**: scan `Index/Presentation.iwa` binary for varint IDs matching file trailing numbers → use byte offset as order; fallback to `index.apxl` XML; last resort = mtime sort
- **Limitation**: slides that are purely image-only but whose mt- file is not the largest version may still be missing — this is a Keynote format limitation without the Keynote app
  1. **Data directory**: Look for `st-*.jpg` files (slide thumbnails - these are the actual slide previews)
     - `st-` prefix = Slide Thumbnail (what we want)
     - `mt-` prefix = Media Thumbnail (user-inserted images/videos, not slides)
     - Filename format: `st-{UUID}-{number}.jpg` where number indicates slide order
     - Example: `st-875C55A8-466B-4362-9DC4-449E9C86ED29-9097.jpg`
  2. **QuickLook directory**: Fallback preview images (usually just 1-2 preview images)
  3. **Root preview files**: `preview.jpg`, `preview-micro.jpg`, `preview-web.jpg` (same image, different resolutions)
  4. **macOS qlmanage**: Generate thumbnails as last resort (only creates 1 preview)
- ⚠️ **Important**: Root preview files are all the same image at different resolutions - loading all 3 would show the same slide 3 times!
- ⚠️ **Limitation**: If Keynote doesn't store `st-` files internally, only 1 slide will load
- 💡 **Best Practice**: Export Keynote to PDF or PPTX for guaranteed multi-slide extraction
- 🧹 **Cleanup**: Temporary extraction directory is automatically deleted after loading

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
2. Launches a coroutine on `Dispatchers.IO` that renders the file using reflection-based POI/PDFBox (same libraries as `PresentationViewModel` but without touching UI state).
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

