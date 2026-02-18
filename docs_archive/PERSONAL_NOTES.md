# Personal Development Notes - ChurchPresenter Project

## 🧹 Cleanup Reminders

### Self-Note: Code Hygiene Checklist

Before considering any task complete:

1. **Imports**
   - ✅ No wildcard imports (`import ... .*`)
   - ✅ Remove unused imports
   - ✅ Material 3 only (not Material 2)

2. **Strings**
   - ✅ User-facing → `strings.xml`
   - ✅ Technical → `Constants.kt`
   - ✅ No hardcoded strings in UI

3. **Cleanup**
   - ⚠️ Remove unused functions/properties
   - ⚠️ Remove debug statements (`println`, logging)
   - ⚠️ Remove commented-out code
   - ⚠️ Fix or document compiler warnings

4. **Current Known Issues**
   - `PresenterManager.kt`: Has unused properties/functions (documented in TODO_CLEANUP.md)
   - `BibleSettingsTab.kt`: Has unused parameters (needs refactoring)
   - Placeholder tabs (Media, Pictures, Announcements): Have unused modifiers (acceptable for now)

### Quick Self-Check Commands

```bash
# Before any commit, run these:
./gradlew compileKotlinJvm --no-daemon 2>&1 | grep -E "(Unused|never used)" | wc -l
# Should be 0 or close to it

grep -r "println\|print(" --include="*.kt" composeApp/src/jvmMain/kotlin/ | wc -l
# Should be 0

grep -r "import.*\.\*" --include="*.kt" composeApp/src/ | wc -l
# Should be 0
```

### Documentation Files Created

1. **CODING_STANDARDS_SUMMARY.md** - Main standards document
2. **TODO_CLEANUP.md** - Specific cleanup tasks to address
3. **PERSONAL_NOTES.md** (this file) - Development reminders

### Mantras to Remember

> "If it's unused, remove it or document why it stays"
> "No wildcards, no hardcoded strings, no debug prints"
> "Always use Material 3, always use string resources"
> "Clean code is working code that reads well"
> "Never use fully qualified type names when import exists"

### Code Style Rules

#### Type Names in Parameters
❌ **WRONG** - Fully qualified type names:
```kotlin
fun myFunction(width: androidx.compose.ui.unit.Dp = 120.dp)
fun myFunction(color: androidx.compose.ui.graphics.Color = Color.Red)
```

✅ **CORRECT** - Import and use simple name:
```kotlin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color

fun myFunction(width: Dp = 120.dp)
fun myFunction(color: Color = Color.Red)
```

**Why**: Fully qualified names are redundant when imports exist, clutter code, and trigger IDE warnings.

---

**Last Updated**: February 18, 2026
**Current Phase**: Standards enforcement and codebase cleanup

