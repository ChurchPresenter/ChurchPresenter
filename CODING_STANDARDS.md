# Coding Standards for ChurchPresenter

## Import Guidelines

### ✅ DO: Use proper imports
```kotlin
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel

fun myFunction(
    item: ScheduleItem,
    section: LyricSection,
    viewModel: BibleViewModel
) { ... }
```

### ❌ DON'T: Use fully qualified class names
```kotlin
// WRONG - Don't do this!
fun myFunction(
    item: org.churchpresenter.app.churchpresenter.models.ScheduleItem,
    section: org.churchpresenter.app.churchpresenter.models.LyricSection,
    viewModel: org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
) { ... }
```

### Import Style Rules

1. **Always import classes explicitly** - Add the proper import statement at the top of the file
2. **Never use wildcard imports** - Avoid `import org.churchpresenter.app.churchpresenter.models.*`
3. **Remove unused imports** - Clean up imports that are no longer needed
4. **Group imports properly** - Keep imports organized by package

## String Resources

### ✅ DO: Use string resources for user-facing text
```kotlin
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_tab

Text(stringResource(Res.string.bible_tab))
```

### ❌ DON'T: Use hard-coded strings for user-facing text
```kotlin
// WRONG - Don't do this!
Text("Bible")
```

### ✅ DO: Use constants for non-user-facing strings
```kotlin
object Constants {
    const val PRIMARY_BIBLE_KEY = "primary_bible"
    const val SECONDARY_BIBLE_KEY = "secondary_bible"
}
```

## Type Declarations

### ✅ DO: Use proper type imports for Compose
```kotlin
import androidx.compose.ui.unit.Dp

width: Dp = 120.dp
```

### ❌ DON'T: Use fully qualified types
```kotlin
// WRONG - Don't do this!
width: androidx.compose.ui.unit.Dp = 120.dp
```

## Material Design

### ✅ DO: Always use Material3
```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
```

### ❌ DON'T: Mix Material2 and Material3
```kotlin
// WRONG - Don't do this!
import androidx.compose.material.Button // Material2
import androidx.compose.material3.Text  // Material3
```

## Code Organization

### Clean Up Unused Code
- Remove unused imports regularly
- Delete commented-out code
- Remove unused functions and classes

### Avoid Code Duplication
- Extract reusable components into separate functions
- Move shared code into utility files
- Use composition over copy-paste

## Documentation

### Document Public APIs
```kotlin
/**
 * Displays a tab for managing Bible verses.
 *
 * @param modifier The modifier to apply to this layout
 * @param viewModel The view model containing Bible data
 * @param onVerseSelected Callback when a verse is selected
 */
@Composable
fun BibleTab(
    modifier: Modifier = Modifier,
    viewModel: BibleViewModel,
    onVerseSelected: (List<SelectedVerse>) -> Unit
) { ... }
```

## File Organization

### Markdown Documentation
- Consolidate related documentation into single files
- Use clear headings and sections
- Keep documentation up-to-date with code changes

## Best Practices Summary

1. **Use proper imports** - Never fully qualify class names in function signatures
2. **Use string resources** - For all user-facing text
3. **Use constants** - For internal keys and identifiers
4. **Use Material3** - Consistently across the entire app
5. **Clean code** - Remove unused code and imports
6. **Avoid duplication** - Extract and reuse common code
7. **Document** - Add documentation for public APIs
8. **Organize** - Keep files and code well-structured

---

**Remember**: Code is read more often than it's written. Make it clean, consistent, and easy to understand!

