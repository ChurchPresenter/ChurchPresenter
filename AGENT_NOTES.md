# Agent Development Notes

This document tracks coding standards, common mistakes, and debugging notes for the ChurchPresenter project.

## Coding Standards

### 1. Import Statements
- ❌ **NEVER** use wildcard imports like `import churchpresenter.composeapp.generated.resources.*`
- ✅ **ALWAYS** use explicit imports for each resource

### 2. String Resources
- ❌ **NEVER** use hard-coded strings in user-facing UI
- ✅ **ALWAYS** use string resources from `Res.string.*`
- 📝 **OK** to use constants for internal/non-user-facing strings

### 3. Material Design
- ✅ **ALWAYS** use Material 3 (`androidx.compose.material3.*`)
- ❌ **AVOID** mixing Material 2 components

### 4. Type Annotations
- ❌ **AVOID**: `width: androidx.compose.ui.unit.Dp = 120.dp`
- ✅ **PREFER**: `width: Dp = 120.dp`

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

## Document Purpose

This is an **AGENT-ONLY** document. It helps me (the AI agent):
- Remember coding standards across sessions
- Track ongoing debug sessions
- Avoid repeating mistakes
- Document solutions for similar future issues

The user should not need to read this document.

