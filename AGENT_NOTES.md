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

### Current Debug Session (2026-02-19)

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

