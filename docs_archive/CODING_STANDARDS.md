# ChurchPresenter Coding Standards

This document outlines the coding standards and best practices for the ChurchPresenter project.

## Import Rules

### ❌ NEVER Use Wildcard Imports
**Do NOT use:**
```kotlin
import churchpresenter.composeapp.generated.resources.*
import androidx.compose.material.icons.Icons.*
import org.jetbrains.compose.resources.*
```

**ALWAYS use explicit imports:**
```kotlin
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_folder
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
```

**Why?**
- Makes it clear which resources are actually being used
- Prevents naming conflicts
- Makes code easier to understand and maintain
- Helps with refactoring and finding unused resources
- Better IDE support and navigation

## String Handling Rules

### Rule 1: User-Facing Strings → String Resources
**All strings visible to users MUST be in `strings.xml`**

❌ **NEVER do this:**
```kotlin
Text("New Schedule")
contentDescription = "Move Up"
label = "Song Title"
```

✅ **ALWAYS do this:**
```kotlin
Text(stringResource(Res.string.new_schedule))
contentDescription = stringResource(Res.string.move_up)
label = stringResource(Res.string.song_title)
```

**Benefits:**
- Enables internationalization (i18n)
- Centralized text management
- Easy to update UI text without code changes
- Supports multiple languages

### Rule 2: Non-User-Facing Strings → Constants
**For technical strings, database keys, file paths, etc., use constants**

❌ **NEVER hardcode technical strings:**
```kotlin
if (type == "song") { ... }
val fileName = "settings.json"
database.query("SELECT * FROM songs")
```

✅ **ALWAYS use constants:**
```kotlin
object ContentType {
    const val SONG = "song"
    const val BIBLE = "bible"
}

object FileNames {
    const val SETTINGS = "settings.json"
    const val DATABASE = "songs.db"
}

object SqlQueries {
    const val SELECT_ALL_SONGS = "SELECT * FROM songs"
}

// Usage
if (type == ContentType.SONG) { ... }
val fileName = FileNames.SETTINGS
```

**Benefits:**
- Type-safe
- Prevents typos
- Single source of truth
- Easy to refactor
- Better IDE support with autocomplete

## String Resource Organization

### File Location
All string resources should be in:
```
composeApp/src/jvmMain/composeResources/values/strings.xml
```

### Naming Convention
Use descriptive, hierarchical names:

```xml
<!-- UI Actions -->
<string name="action_new">New</string>
<string name="action_open">Open</string>
<string name="action_save">Save</string>
<string name="action_delete">Delete</string>

<!-- Tooltips -->
<string name="tooltip_new_schedule">New Schedule</string>
<string name="tooltip_move_up">Move Up</string>
<string name="tooltip_go_live">Go Live</string>

<!-- Labels -->
<string name="label_song_title">Song Title</string>
<string name="label_song_number">Song Number</string>

<!-- Dialog Titles -->
<string name="dialog_settings">Settings</string>
<string name="dialog_edit_song">Edit Song</string>

<!-- Messages -->
<string name="message_save_success">Saved successfully</string>
<string name="message_error">An error occurred</string>
```

## Constants Organization

### File Structure
Create constant objects by domain:

```kotlin
// ContentTypes.kt
package org.churchpresenter.app.churchpresenter.constants

object ContentType {
    const val SONG = "song"
    const val BIBLE = "bible"
    const val ANNOUNCEMENT = "announcement"
}

// DatabaseConstants.kt
package org.churchpresenter.app.churchpresenter.constants

object DatabaseConstants {
    const val SONGS_TABLE = "songs"
    const val BIBLE_TABLE = "verses"
    const val SCHEDULE_TABLE = "schedule"
    
    object Columns {
        const val ID = "id"
        const val TITLE = "title"
        const val NUMBER = "number"
    }
}

// FileConstants.kt
package org.churchpresenter.app.churchpresenter.constants

object FileConstants {
    const val SETTINGS_FILE = "settings.json"
    const val SONGS_DIRECTORY = "songs"
    const val BIBLE_DIRECTORY = "bibles"
    const val SONG_FILE_EXTENSION = ".sps"
    const val BIBLE_FILE_EXTENSION = ".spb"
}
```

## Quick Reference

| Type | Rule | Example |
|------|------|---------|
| **User-facing text** | String resources | `stringResource(Res.string.title)` |
| **Button labels** | String resources | `stringResource(Res.string.save)` |
| **Tooltips** | String resources | `stringResource(Res.string.tooltip_add)` |
| **Error messages** | String resources | `stringResource(Res.string.error_save)` |
| **Database keys** | Constants | `DatabaseConstants.Columns.ID` |
| **File names** | Constants | `FileConstants.SETTINGS_FILE` |
| **Type identifiers** | Constants | `ContentType.SONG` |
| **File extensions** | Constants | `FileConstants.SONG_FILE_EXTENSION` |
| **SQL queries** | Constants | `SqlQueries.SELECT_ALL` |

## Exception Cases

The ONLY acceptable hardcoded strings are:
1. **Debug/logging messages** - Can use string literals
   ```kotlin
   println("DEBUG: Loading songs from database")
   ```

2. **Test strings** - Test code can use string literals
   ```kotlin
   assertEquals("test", result)
   ```

3. **Regex patterns** - Technical patterns are OK
   ```kotlin
   val pattern = Regex("[0-9]+")
   ```

## Enforcement

Before committing code, verify:
- ✅ No wildcard imports (`.*`)
- ✅ No hardcoded user-facing strings
- ✅ All technical strings in constants
- ✅ All UI strings in `strings.xml`
- ✅ All imports are explicit

## Migration Guide

When you find hardcoded strings:

1. **Identify if it's user-facing**
   - User-facing? → Add to `strings.xml`
   - Technical? → Add to constants file

2. **Add to appropriate location**
   - String resource: `strings.xml`
   - Constant: Create/update constants file

3. **Replace in code**
   - Use `stringResource(Res.string.key)` for UI strings
   - Use `Constants.VALUE` for technical strings

4. **Test the change**
   - Ensure compilation succeeds
   - Verify UI displays correctly

## Why These Rules Matter

1. **Internationalization**: Easy to translate to multiple languages
2. **Maintainability**: Change text in one place, updates everywhere
3. **Type Safety**: Constants prevent typos and errors
4. **Code Clarity**: Explicit imports make dependencies clear
5. **Professionalism**: Clean, maintainable, scalable code
6. **Team Collaboration**: Consistent codebase for all contributors

---

**Remember:** These rules are NOT optional. They are critical for maintaining a professional, scalable, and maintainable codebase. Always follow them!

Last Updated: February 18, 2026

