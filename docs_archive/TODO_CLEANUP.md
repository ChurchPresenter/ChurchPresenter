# Code Cleanup TODO List

## 📝 High Priority - Unused Code & Imports

### Files with Unused Properties/Functions

#### PresenterManager.kt
- [ ] **Unused Property**: `selectedVerse: State<SelectedVerse>` (line 14)
  - Consider removing if not needed, or document why it's kept for future use
- [ ] **Unused Function**: `setSelectedVerse(verse: SelectedVerse)` (line 29)
  - Used to set single verse, but we now use `setSelectedVerses()` with list
  - Consider removing if truly not needed
- [ ] **Unused Function**: `togglePresenterWindow()` (line 44)
  - May be needed for future toggle functionality
  - Consider documenting or removing

#### BibleSettingsTab.kt
- [ ] **Unused Parameter**: `availableFonts: List<String>` in multiple functions
  - Parameter passed but never used in function body
  - Either use it or remove it from signature

#### MediaTab.kt, PicturesTab.kt, AnnouncementsTab.kt
- [ ] **Unused Parameter**: `modifier: Modifier` in all three tabs
  - These are placeholder tabs with minimal implementation
  - Either apply modifier or suppress warning

### General Cleanup Tasks

#### Import Cleanup
- [x] ✅ Removed all wildcard imports (`import ... .*`)
- [x] ✅ Fixed Material 2 to Material 3 imports
- [ ] Run IDE "Optimize Imports" on entire project to catch any remaining issues

#### String Resources
- [x] ✅ Most user-facing strings moved to strings.xml
- [ ] Check for any remaining hardcoded UI strings
- [ ] Verify all strings.xml entries are actually used

#### Constants
- [x] ✅ Technical strings moved to Constants.kt
- [ ] Audit Constants.kt for unused constants

#### Warnings to Address
- [ ] Unnecessary non-null assertions (`!!`) 
  - BibleSettingsTab.kt line 106: `return@use title!!`
  - These should be replaced with safer null handling

### Automated Checks to Run

```bash
# Find all unused imports (run from project root)
./gradlew compileKotlinJvm 2>&1 | grep "Unused import"

# Find all unused parameters
./gradlew compileKotlinJvm 2>&1 | grep "never used"

# Find potential hardcoded strings
grep -r "Text(\"" --include="*.kt" composeApp/src/jvmMain/kotlin/ | grep -v stringResource

# Find unnecessary non-null assertions
grep -r "!!" --include="*.kt" composeApp/src/jvmMain/kotlin/
```

### Best Practices Going Forward

1. **Before Committing**:
   - Run "Optimize Imports" in IDE
   - Check compiler warnings
   - Fix or suppress unused warnings appropriately

2. **When Adding New Code**:
   - Only import what you use
   - Remove debug code and unused functions
   - Use `@Suppress("unused")` annotation if keeping for API consistency

3. **Regular Maintenance**:
   - Monthly audit of unused code
   - Remove commented-out code
   - Update documentation for "unused but intentional" code

### Decision Log

**Why some "unused" code is kept**:
- API methods may be unused now but needed for completeness
- Placeholder tabs (Media, Pictures, Announcements) kept for future implementation
- Some properties exposed as State for potential future reactive UI needs

**What should definitely be removed**:
- Truly dead code with no future purpose
- Duplicate implementations
- Old commented-out code blocks

---

**Created**: February 18, 2026
**Status**: 🟡 In Progress
**Priority**: Medium (cleanup improves maintainability but doesn't affect functionality)

## Quick Cleanup Script

```bash
#!/bin/bash
# Run this to generate a cleanup report

echo "=== Checking for unused imports ==="
./gradlew compileKotlinJvm --no-daemon 2>&1 | grep "Unused import" | wc -l

echo "=== Checking for unused parameters ==="
./gradlew compileKotlinJvm --no-daemon 2>&1 | grep "Parameter.*never used" | wc -l

echo "=== Checking for unused functions ==="
./gradlew compileKotlinJvm --no-daemon 2>&1 | grep "Function.*never used" | wc -l

echo "=== Checking for unused properties ==="
./gradlew compileKotlinJvm --no-daemon 2>&1 | grep "Property.*never used" | wc -l

echo "=== Done ==="
```

Save this as `cleanup_check.sh` and run with `bash cleanup_check.sh`

