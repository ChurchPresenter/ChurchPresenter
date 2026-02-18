# Coding Standards Summary

## ✅ Completed Tasks

### 1. Eliminated ALL Wildcard Imports
All `import ... .*` statements have been replaced with explicit imports across the entire codebase.

**Files Fixed:**
- ✅ `composables/AlignmentButtons.kt` - Fixed resource imports
- ✅ `main.kt` - Fixed runtime imports  
- ✅ `viewmodel/SongsViewModel.kt` - Fixed runtime imports
- ✅ `viewmodel/BibleViewModel.kt` - Fixed runtime imports
- ✅ `viewmodel/PresenterManager.kt` - Fixed runtime imports
- ✅ `composables/ColorPickerField.kt` - Fixed layout and runtime imports
- ✅ `ui/theme/Theme.kt` - Fixed Material 3 imports
- ✅ `ui/theme/ThemeManager.kt` - Fixed runtime imports
- ✅ `MainDesktop.kt` - Fixed key event imports
- ✅ `tabs/SongsTab.kt` - Fixed key event imports

**Verification:** Run `grep -r "import.*\.\*" --include="*.kt"` returns NO results ✅

### 2. String Resources Implementation
All user-facing strings are now using `stringResource(Res.string.*)` pattern.

**String Resources Location:**
```
composeApp/src/jvmMain/composeResources/values/strings.xml
```

**Pattern Followed:**
```kotlin
// ❌ OLD (Never use)
Text("Save")
tooltip = "Move Up"

// ✅ NEW (Always use)
Text(stringResource(Res.string.save))
tooltip = stringResource(Res.string.tooltip_move_up)
```

### 3. Constants Implementation
Technical strings (file extensions, content types, database keys) use constants.

**Constants Location:**
```
composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/utils/Constants.kt
```

**Pattern Followed:**
```kotlin
// ❌ OLD (Never use)
if (type == "song") { ... }
val ext = ".sps"

// ✅ NEW (Always use)  
if (type == ContentType.SONG) { ... }
val ext = FileConstants.SONG_FILE_EXTENSION
```

## 📋 Current Coding Standards

### Import Rules
1. **NEVER** use wildcard imports (`import ... .*`)
2. **ALWAYS** use explicit imports
3. Group imports logically (Compose, Material, Resources, App)

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

### String Rules
1. **User-facing strings** → `strings.xml` + `stringResource()`
2. **Technical strings** → Constants object
3. **Debug/logging** → Can use string literals
4. **NO hardcoded strings** in UI code

### Organization
1. String resources: `composeResources/values/strings.xml`
2. Constants: `utils/Constants.kt`
3. Naming: Use descriptive hierarchical names

### Code Cleanup
1. **Remove unused imports** - Run IDE "Optimize Imports" before committing
2. **Remove unused functions/properties** - Or document why they're kept
3. **Remove commented-out code** - Use version control instead
4. **Fix compiler warnings** - Or suppress with justification
5. **No debug print statements** - Use proper logging or remove

## 🔍 Quick Verification Commands

```bash
# Check for wildcard imports (should return nothing)
grep -r "import.*\.\*" --include="*.kt" composeApp/src/

# Check for Material 2 usage (should return nothing)
grep -r "import androidx.compose.material\." --include="*.kt" composeApp/src/ | grep -v "material3"
grep -r "import androidx.compose.material\.[^3]" --include="*.kt" composeApp/src/

# Check for potential hardcoded strings in UI
grep -r "Text(\"" --include="*.kt" composeApp/src/jvmMain/kotlin/

# Check for proper string resource usage
grep -r "stringResource(Res.string" --include="*.kt" composeApp/src/

# Check for unused code (generates warnings)
./gradlew compileKotlinJvm --no-daemon 2>&1 | grep -E "(Unused|never used)"

# Check for debug prints that should be removed
grep -rE "(println|print\()" --include="*.kt" composeApp/src/jvmMain/kotlin/
```

## 📝 Next Steps for New Code

When adding new features:

1. **Add string resources FIRST**
   - Add to `strings.xml` before writing UI code
   - Use descriptive names with prefixes (action_, label_, tooltip_, etc.)

2. **Add constants FIRST**
   - Add to `Constants.kt` before using in logic
   - Group by domain (file names, content types, etc.)

3. **Import explicitly**
   - Let IDE auto-import (it will NOT use wildcards)
   - Review imports before committing

4. **Review before commit**
   - No `.*` imports
   - No hardcoded UI strings
   - All technical strings in constants
   - **No unused imports or code**
   - **No debug print statements**
   - **All compiler warnings addressed**

## ✅ Benefits Achieved

1. **Maintainability** - Easy to find and update strings
2. **Internationalization** - Ready for translation
3. **Type Safety** - Constants prevent typos
4. **Code Clarity** - Explicit imports show dependencies
5. **Professionalism** - Clean, organized codebase
6. **Scalability** - Easy to extend and refactor

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

All violations must be fixed before merging!

---

**Last Updated:** February 18, 2026
**Status:** ✅ All standards implemented and enforced




