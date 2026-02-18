# 📚 ChurchPresenter Documentation

## Main Documentation

👉 **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)** - Complete development guide including:
- Coding standards
- Style rules
- String management
- Cleanup tasks
- Verification commands
- Development workflow

## Quick Reference

### Zero Tolerance Rules
❌ No wildcard imports  
❌ No hardcoded UI strings  
❌ No Material 2 components  
❌ No fully qualified type names  
❌ No debug print statements  

### Before Every Commit
```bash
# Run these checks
bash cleanup_check.sh

# Or manually:
grep -r "import.*\.\*" --include="*.kt" composeApp/src/ | wc -l  # Should be 0
grep -rE "(println|print\()" --include="*.kt" composeApp/src/jvmMain/kotlin/ | wc -l  # Should be 0
./gradlew compileKotlinJvm --no-daemon 2>&1 | grep -E "(Unused|never used)"  # Review output
```

### Quick Style Rules

✅ **DO:**
```kotlin
import androidx.compose.ui.unit.Dp
fun myFunction(width: Dp = 120.dp)
Text(stringResource(Res.string.save))
```

❌ **DON'T:**
```kotlin
import androidx.compose.ui.unit.*
fun myFunction(width: androidx.compose.ui.unit.Dp = 120.dp)
Text("Save")
```

## File Locations

- **String Resources:** `composeApp/src/jvmMain/composeResources/values/strings.xml`
- **Constants:** `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/utils/Constants.kt`
- **Cleanup Tasks:** See DEVELOPMENT_GUIDE.md § Cleanup TODO List

## Archived Documentation

The following files have been consolidated into DEVELOPMENT_GUIDE.md:
- ~~CODING_STANDARDS_SUMMARY.md~~ → Now in DEVELOPMENT_GUIDE.md
- ~~TODO_CLEANUP.md~~ → Now in DEVELOPMENT_GUIDE.md § Cleanup TODO List
- ~~PERSONAL_NOTES.md~~ → Now in DEVELOPMENT_GUIDE.md

---

**For full details, see [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)**

