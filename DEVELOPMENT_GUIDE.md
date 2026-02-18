# ChurchPresenter Development Guide

> **Comprehensive documentation for coding standards, cleanup tasks, and development practices**
>
> Last Updated: February 18, 2026

---

## 📚 Table of Contents

1. [Zero Tolerance Policy](#-zero-tolerance-policy)
2. [Coding Standards](#-coding-standards)
3. [Code Style Rules](#-code-style-rules)
4. [String Management](#-string-management)
5. [Cleanup TODO List](#-cleanup-todo-list)
6. [Verification Commands](#-verification-commands)
7. [Development Workflow](#-development-workflow)

---

## 🎯 Zero Tolerance Policy

The following are **NOT** acceptable in any commit:

❌ Wildcard imports (`import ... .*`)  
❌ Hardcoded UI strings (`Text("Save")`)  
❌ Magic strings in logic (`if (type == "song")`)  
❌ Unnamed color values (`Color(0xFF123456)`)  
❌ Material 2 components (`androidx.compose.material.*`)  
❌ Legacy UI components (use Material 3 only)  
❌ Unused imports (run "Optimize Imports")  
❌ Debug print statements (`println()`, `print()`)  
❌ Commented-out code blocks  
❌ Fully qualified type names (`androidx.compose.ui.unit.Dp`)  

**All violations must be fixed before merging!**

---

## 📋 Coding Standards

### Import Rules

1. **NEVER** use wildcard imports (`import ... .*`)
2. **ALWAYS** use explicit imports
3. Group imports logically (Compose, Material, Resources, App)
4. Remove unused imports before committing

**Status:** ✅ All wildcard imports eliminated from codebase

### Material Design Rules

1. **ALWAYS** use Material 3 components
2. **NEVER** use Material 2 or legacy components
3. Import from `androidx.compose.material3.*` (not `material.*`)
4. Use Material 3 icons: `androidx.compose.material.icons.*`

**Examples:**

```kotlin
// ❌ WRONG - Material 2
import androidx.compose.material.Button
import androidx.compose.material.Text

// ✅ CORRECT - Material 3
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
```

### Code Cleanup Standards

1. **Remove unused imports** - Run IDE "Optimize Imports" before committing
2. **Remove unused functions/properties** - Or document why they're kept
3. **Remove commented-out code** - Use version control instead
4. **Fix compiler warnings** - Or suppress with justification
5. **No debug print statements** - Use proper logging or remove

---

## 🎨 Code Style Rules

### Type Names in Parameters

❌ **WRONG** - Fully qualified type names:
```kotlin
fun myFunction(width: androidx.compose.ui.unit.Dp = 120.dp)
fun myFunction(color: androidx.compose.ui.graphics.Color = Color.Red)
fun myRow(modifier: androidx.compose.ui.Modifier = Modifier)
```

✅ **CORRECT** - Import and use simple name:
```kotlin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier

fun myFunction(width: Dp = 120.dp)
fun myFunction(color: Color = Color.Red)
fun myRow(modifier: Modifier = Modifier)
```

**Why**: Fully qualified names are redundant when imports exist, clutter code, and trigger IDE warnings ("Remove redundant qualifier name").

### Import Organization

```kotlin
// Group 1: Compose Foundation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*

// Group 2: Material 3
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme

// Group 3: Runtime & UI
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

// Group 4: Resources
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.save

// Group 5: App Classes
import org.churchpresenter.app.churchpresenter.models.*
import org.churchpresenter.app.churchpresenter.viewmodel.*
```

---

## 📝 String Management

### User-Facing Strings

**Location:** `composeApp/src/jvmMain/composeResources/values/strings.xml`

**Pattern:**
```kotlin
// ❌ OLD (Never use)
Text("Save")
tooltip = "Move Up"
Button(onClick = {}) { Text("Cancel") }

// ✅ NEW (Always use)
Text(stringResource(Res.string.save))
tooltip = stringResource(Res.string.tooltip_move_up)
Button(onClick = {}) { Text(stringResource(Res.string.cancel)) }
```

**Naming Convention:**
- Actions: `action_save`, `action_delete`
- Labels: `label_song_title`, `label_author`
- Tooltips: `tooltip_move_up`, `tooltip_add_to_schedule`
- Messages: `message_no_songs_found`
- Errors: `error_file_not_found`

### Technical Strings (Constants)

**Location:** `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/utils/Constants.kt`

**Pattern:**
```kotlin
// ❌ OLD (Never use)
if (type == "song") { ... }
val ext = ".sps"
if (mode == "light") { ... }

// ✅ NEW (Always use)
if (type == ContentType.SONG) { ... }
val ext = FileConstants.SONG_FILE_EXTENSION
if (mode == ThemeMode.LIGHT) { ... }
```

**Organization:**
```kotlin
object Constants {
    object FileConstants {
        const val SONG_FILE_EXTENSION = ".sps"
        const val BIBLE_FILE_EXTENSION = ".spb"
    }
    
    object ContentType {
        const val SONG = "song"
        const val BIBLE = "bible"
    }
}
```

---

## 🧹 Cleanup TODO List

### High Priority - Unused Code

#### PresenterManager.kt
- [ ] **Unused Property**: `selectedVerse: State<SelectedVerse>` (line 14)
  - Currently using `selectedVerses` list instead
  - Consider removing or document reason for keeping
  
- [ ] **Unused Function**: `setSelectedVerse(verse: SelectedVerse)` (line 29)
  - Now using `setSelectedVerses(verses: List<SelectedVerse>)`
  - Safe to remove if single verse mode not needed
  
- [ ] **Unused Function**: `togglePresenterWindow()` (line 44)
  - May be needed for future toggle functionality
  - Either implement or remove

**Action:** Review if single-verse mode is needed for future features

#### BibleSettingsTab.kt
- [ ] **Unused Parameter**: `availableFonts: List<String>` in multiple functions
  - Parameter passed through chain but never used
  - Either implement font selection or remove parameter

#### Placeholder Tabs
- [ ] **MediaTab.kt**: Unused `modifier` parameter (acceptable - placeholder)
- [ ] **PicturesTab.kt**: Unused `modifier` parameter (acceptable - placeholder)
- [ ] **AnnouncementsTab.kt**: Unused `modifier` parameter (acceptable - placeholder)

### General Cleanup Tasks

#### Completed ✅
- [x] Removed all wildcard imports (`import ... .*`)
- [x] Fixed Material 2 to Material 3 imports
- [x] Most user-facing strings moved to strings.xml
- [x] Technical strings moved to Constants.kt

#### Pending ⚠️
- [ ] Run IDE "Optimize Imports" on entire project
- [ ] Check for remaining hardcoded UI strings
- [ ] Verify all strings.xml entries are used
- [ ] Audit Constants.kt for unused constants
- [ ] Replace unnecessary non-null assertions (`!!`)

### Decision Log

**Why some "unused" code is kept:**
- API methods may be unused now but needed for completeness
- Placeholder tabs kept for future implementation
- Some properties exposed as State for potential future reactive UI needs
- Single-verse mode in PresenterManager might be needed later

**What should be removed:**
- Truly dead code with no future purpose
- Duplicate implementations
- Old commented-out code blocks
- Debug logging statements

---

## 🔍 Verification Commands

### Before Every Commit

```bash
# Check for wildcard imports (should return 0)
grep -r "import.*\.\*" --include="*.kt" composeApp/src/ | wc -l

# Check for Material 2 usage (should return 0)
grep -r "import androidx.compose.material\.[^3]" --include="*.kt" composeApp/src/ | wc -l

# Check for debug prints (should return 0)
grep -rE "(println|print\()" --include="*.kt" composeApp/src/jvmMain/kotlin/ | wc -l

# Check for unused code (review output)
./gradlew compileKotlinJvm --no-daemon 2>&1 | grep -E "(Unused|never used)"
```

### Detailed Checks

```bash
# Find potential hardcoded strings
grep -r "Text(\"" --include="*.kt" composeApp/src/jvmMain/kotlin/ | grep -v stringResource

# Find unnecessary non-null assertions
grep -r "!!" --include="*.kt" composeApp/src/jvmMain/kotlin/

# Find fully qualified type names
grep -r "androidx\\.compose\\.[a-z]*\\.[a-zA-Z]*\\.[A-Z]" --include="*.kt" composeApp/src/
```

### Cleanup Report Script

Create `cleanup_check.sh`:
```bash
#!/bin/bash
echo "=== ChurchPresenter Code Quality Report ==="
echo ""
echo "Wildcard imports: $(grep -r 'import.*\.\*' --include='*.kt' composeApp/src/ | wc -l)"
echo "Material 2 imports: $(grep -r 'import androidx.compose.material\.[^3]' --include='*.kt' composeApp/src/ | wc -l)"
echo "Debug prints: $(grep -rE '(println|print\()' --include='*.kt' composeApp/src/jvmMain/kotlin/ | wc -l)"
echo "Unused imports: $(./gradlew compileKotlinJvm --no-daemon 2>&1 | grep 'Unused import' | wc -l)"
echo "Unused parameters: $(./gradlew compileKotlinJvm --no-daemon 2>&1 | grep 'Parameter.*never used' | wc -l)"
echo "Unused functions: $(./gradlew compileKotlinJvm --no-daemon 2>&1 | grep 'Function.*never used' | wc -l)"
echo "Unused properties: $(./gradlew compileKotlinJvm --no-daemon 2>&1 | grep 'Property.*never used' | wc -l)"
echo ""
echo "=== All counts should be 0 or near 0 ==="
```

Run with: `bash cleanup_check.sh`

---

## 🚀 Development Workflow

### Pre-Coding Checklist

1. **Add string resources FIRST**
   - Add to `strings.xml` before writing UI code
   - Use descriptive names with prefixes

2. **Add constants FIRST**
   - Add to `Constants.kt` before using in logic
   - Group by domain (file names, content types, etc.)

3. **Import explicitly**
   - Let IDE auto-import (it will NOT use wildcards)
   - Review imports before committing

### Pre-Commit Checklist

- [ ] No wildcard imports (`import ... .*`)
- [ ] No hardcoded UI strings
- [ ] All technical strings use constants
- [ ] No unused imports (run "Optimize Imports")
- [ ] No debug print statements
- [ ] All compiler warnings addressed or documented
- [ ] No fully qualified type names in parameters
- [ ] Material 3 only (no Material 2)
- [ ] No commented-out code blocks

### Adding New Features

1. **Plan strings first** - Add to `strings.xml`
2. **Plan constants** - Add to `Constants.kt`
3. **Write code** - Use string resources and constants
4. **Test** - Verify functionality
5. **Clean up** - Remove unused code, optimize imports
6. **Verify** - Run verification commands
7. **Commit** - With clean, standards-compliant code

---

## 💡 Developer Mantras

> **"If it's unused, remove it or document why it stays"**

> **"No wildcards, no hardcoded strings, no debug prints"**

> **"Always use Material 3, always use string resources"**

> **"Never use fully qualified type names when import exists"**

> **"Clean code is working code that reads well"**

---

## ✅ Benefits Achieved

1. **Maintainability** - Easy to find and update strings
2. **Internationalization** - Ready for translation
3. **Type Safety** - Constants prevent typos
4. **Code Clarity** - Explicit imports show dependencies
5. **Professionalism** - Clean, organized codebase
6. **Scalability** - Easy to extend and refactor
7. **Consistency** - Uniform style across project
8. **Quality** - Compiler warnings addressed

---

## 📊 Current Status

**Overall Progress:** 🟢 Standards Implemented & Enforced

| Area | Status | Notes |
|------|--------|-------|
| Wildcard Imports | ✅ Complete | All removed |
| Material 3 | ✅ Complete | All Material 2 converted |
| String Resources | ✅ Mostly Complete | Few technical strings remain |
| Constants | ✅ Complete | All magic strings eliminated |
| Unused Code | 🟡 In Progress | See cleanup TODO list |
| Code Style | ✅ Complete | Fully qualified names fixed |

---

**Document Version:** 1.0  
**Last Updated:** February 18, 2026  
**Maintained By:** Development Team  
**Status:** 📘 Active Reference Document

