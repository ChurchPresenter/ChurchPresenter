package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.delete_saved_string
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_save
import churchpresenter.composeapp.generated.resources.lower_third_folder_hint
import churchpresenter.composeapp.generated.resources.lower_third_no_files
import churchpresenter.composeapp.generated.resources.no_saved_strings
import churchpresenter.composeapp.generated.resources.play
import churchpresenter.composeapp.generated.resources.replace
import churchpresenter.composeapp.generated.resources.save_string
import churchpresenter.composeapp.generated.resources.search
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.ImageIconButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

// One search→replace pair
private data class SearchReplacePairState(val search: String = "", val replace: String = "")

@Composable
fun StreamingTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {}
) {
    val folder = appSettings.streamingSettings.lowerThirdFolder

    val jsonFiles = remember(folder) {
        if (folder.isBlank()) emptyList()
        else File(folder)
            .takeIf { it.exists() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" }
            ?.sortedBy { it.nameWithoutExtension.lowercase() }
            ?: emptyList()
    }

    var selectedFile by remember(jsonFiles) { mutableStateOf(jsonFiles.firstOrNull()) }
    var isPlaying by remember { mutableStateOf(false) }
    var frozenProgress by remember { mutableStateOf(0f) }
    var isFrozen by remember { mutableStateOf(false) }

    // Dynamic list of search/replace pairs — starts with one empty row
    var pairs by remember { mutableStateOf(listOf(SearchReplacePairState())) }

    val rawJson = remember(selectedFile) { selectedFile?.readText() ?: "" }
    val hasEditableText = remember(rawJson) { lottieHasEditableText(rawJson) }

    // Debounced pairs for preview
    var debouncedPairs by remember { mutableStateOf(pairs) }
    LaunchedEffect(pairs) {
        delay(400)
        debouncedPairs = pairs
    }

    val jsonContent = remember(rawJson, debouncedPairs) {
        var result = rawJson
        for (p in debouncedPairs) {
            if (p.search.isNotBlank()) result = replaceLottieText(result, p.search, p.replace)
        }
        result
    }

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent)
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying,
        iterations = 1
    )

    // When animation reaches the end, stop playing and freeze on last frame
    LaunchedEffect(progress) {
        if (progress >= 1f && isPlaying) {
            isPlaying = false
            frozenProgress = 1f
            isFrozen = true
        }
    }

    // Reset frozen state when composition changes (new file / new replacements)
    LaunchedEffect(composition) {
        frozenProgress = 0f
        isFrozen = false
        isPlaying = false
    }

    val displayProgress = if (isFrozen) frozenProgress else progress

    val searchLabel  = stringResource(Res.string.search)
    val replaceLabel = stringResource(Res.string.replace)
    val savedSearch  = appSettings.streamingSettings.savedSearchStrings
    val savedReplace = appSettings.streamingSettings.savedReplaceStrings

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // File selector + play button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (jsonFiles.isEmpty()) {
                Text(
                    text = if (folder.isBlank()) stringResource(Res.string.lower_third_folder_hint)
                           else stringResource(Res.string.lower_third_no_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
            } else {
                DropdownSelector(
                    modifier = Modifier.weight(1f),
                    label = "",
                    items = jsonFiles.map { it.nameWithoutExtension },
                    selected = selectedFile?.nameWithoutExtension ?: "",
                    onSelectedChange = { name ->
                        selectedFile = jsonFiles.find { it.nameWithoutExtension == name }
                        isPlaying = false
                    }
                )
            }
            ImageIconButton(
                onClick = {
                    if (composition != null) {
                        frozenProgress = 0f
                        isFrozen = false
                        isPlaying = true
                    }
                },
                size = 36.dp,
                modifier = Modifier.background(
                    color = if (composition != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                )
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play),
                    contentDescription = stringResource(Res.string.play),
                    modifier = Modifier.size(18.dp),
                    tint = if (composition != null) Color.White
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }

        HorizontalDivider()

        // Dynamic search→replace rows
        pairs.forEachIndexed { index, pair ->
            SearchReplaceRow(
                search = pair.search,
                replace = pair.replace,
                searchLabel = searchLabel,
                replaceLabel = replaceLabel,
                savedSearch = savedSearch,
                savedReplace = savedReplace,
                canRemove = pairs.size > 1,
                onSearchChange = { v -> pairs = pairs.toMutableList().also { it[index] = it[index].copy(search = v) } },
                onReplaceChange = { v -> pairs = pairs.toMutableList().also { it[index] = it[index].copy(replace = v) } },
                onRemove = { pairs = pairs.toMutableList().also { it.removeAt(index) } },
                onSaveSearch = { v ->
                    if (v.isNotBlank() && !savedSearch.contains(v))
                        onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(savedSearchStrings = savedSearch + v)) }
                },
                onDeleteSearch = { v ->
                    onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(savedSearchStrings = savedSearch - v)) }
                },
                onSaveReplace = { v ->
                    if (v.isNotBlank() && !savedReplace.contains(v))
                        onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(savedReplaceStrings = savedReplace + v)) }
                },
                onDeleteReplace = { v ->
                    onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(savedReplaceStrings = savedReplace - v)) }
                }
            )
        }

        // Add row button
        OutlinedButton(
            onClick = { pairs = pairs + SearchReplacePairState() },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("+ ${stringResource(Res.string.search)}", style = MaterialTheme.typography.labelSmall)
        }

        if (selectedFile != null && !hasEditableText) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⚠️ No editable text found in this Lottie file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        HorizontalDivider()

        // Preview — black 16:9 at 50% width
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
            if (composition != null) {
                Image(
                    painter = rememberLottiePainter(composition = composition, progress = { displayProgress }),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                    Text("📽️", style = MaterialTheme.typography.displayLarge, color = Color.White.copy(alpha = 0.3f))
                }
            }
        }
    }
}

/** One row: [Search field 💾 ▾] → [Replace field 💾 ▾] [×] */
@Composable
private fun SearchReplaceRow(
    search: String,
    replace: String,
    searchLabel: String,
    replaceLabel: String,
    savedSearch: List<String>,
    savedReplace: List<String>,
    canRemove: Boolean,
    onSearchChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onRemove: () -> Unit,
    onSaveSearch: (String) -> Unit,
    onDeleteSearch: (String) -> Unit,
    onSaveReplace: (String) -> Unit,
    onDeleteReplace: (String) -> Unit,
) {
    val deleteLabel = stringResource(Res.string.delete_saved_string)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search field + its save/dropdown
        SaveableTextField(
            value = search,
            onValueChange = onSearchChange,
            label = searchLabel,
            savedValues = savedSearch,
            onSave = onSaveSearch,
            onDelete = onDeleteSearch,
            modifier = Modifier.weight(1f)
        )

        // Arrow separator
        Text(
            text = "→",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )

        // Replace field + its save/dropdown
        SaveableTextField(
            value = replace,
            onValueChange = onReplaceChange,
            label = replaceLabel,
            savedValues = savedReplace,
            onSave = onSaveReplace,
            onDelete = onDeleteReplace,
            modifier = Modifier.weight(1f)
        )

        // Remove-row button (hidden when only 1 row)
        ImageIconButton(
            onClick = onRemove,
            enabled = canRemove,
            size = 40.dp
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = deleteLabel,
                modifier = Modifier.size(32.dp),
                tint = if (canRemove) MaterialTheme.colorScheme.error
                       else Color.Transparent
            )
        }
    }
}

/** Text field with inline 💾 save icon and ▾ saved-values dropdown */
@Composable
private fun SaveableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    savedValues: List<String>,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val saveLabel    = stringResource(Res.string.save_string)
    val noSavedLabel = stringResource(Res.string.no_saved_strings)
    val deleteLabel  = stringResource(Res.string.delete_saved_string)

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                ImageIconButton(
                    onClick = { dropdownExpanded = true },
                    enabled = savedValues.isNotEmpty(),
                    size = 36.dp
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_down),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (savedValues.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            },
            trailingIcon = {
                ImageIconButton(
                    onClick = { onSave(value) },
                    enabled = value.isNotBlank() && !savedValues.contains(value),
                    size = 36.dp
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_save),
                        contentDescription = saveLabel,
                        modifier = Modifier.size(18.dp),
                        tint = if (value.isNotBlank() && !savedValues.contains(value))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            }
        )

        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            if (savedValues.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            noSavedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    onClick = { dropdownExpanded = false }
                )
            } else {
                savedValues.sortedBy { it.lowercase() }.forEach { saved ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(saved, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                ImageIconButton(
                                    onClick = { onDelete(saved); dropdownExpanded = false },
                                    size = 18.dp
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_close),
                                        contentDescription = deleteLabel,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        onClick = { onValueChange(saved); dropdownExpanded = false }
                    )
                }
            }
        }
    }
}

/**
 * Replaces [search] with [replacement] in a Lottie JSON.
 *
 * Two-step approach:
 * 1. Replace the "t":"<search>" keyframe text value with "t":"<replacement>"
 * 2. Ensure every character of [replacement] exists in the "chars" atlas.
 *    Missing characters are added by cloning a glyph from the same font family/style
 *    and updating its "ch" field — the paths are approximate but the character is renderable.
 */
private fun replaceLottieText(json: String, search: String, replacement: String): String {
    val escaped = Regex.escape(search)

    // Step 1: replace the keyframe text value
    var result = json.replace(Regex(""""t"\s*:\s*"$escaped"""")) { """"t":"$replacement"""" }

    // Step 2: clear the chars atlas entirely so Lottie falls back to system font rendering.
    // The embedded chars array is a font subset — cloning glyph paths for missing chars
    // just copies wrong bezier curves. An empty atlas forces the renderer to use the
    // font name/style already stored in the keyframe ("f":"Cabin-Italic" etc.)
    result = clearCharsAtlas(result)

    return result
}

/**
 * Replaces the "chars":[...] array with "chars":[] so Lottie uses system font fallback
 * for all characters instead of the embedded subset glyph paths.
 */
private fun clearCharsAtlas(json: String): String {
    val charsStart = Regex(""""chars"\s*:\s*\[""").find(json) ?: return json
    var depth = 1
    var pos = charsStart.range.last + 1
    while (pos < json.length && depth > 0) {
        when (json[pos]) { '[' -> depth++; ']' -> depth-- }
        if (depth > 0) pos++
    }
    // Replace entire chars array content with empty array
    val before = json.substring(0, charsStart.range.first)
    val after = json.substring(pos + 1) // +1 to skip the closing ]
    return """${before}"chars":[]${after}"""
}

/**
 * Returns true if the Lottie JSON contains editable text keyframe values.
 * A file can have BOTH a "chars" font atlas AND editable text layers — the atlas
 * just maps glyphs to paths for rendering, but the actual text string lives in
 * the keyframe "s":{"t":"<value>"} field which IS replaceable.
 */
private fun lottieHasEditableText(json: String): Boolean {
    // Check for keyframe text source: "s":{...,"t":"<value>",...}
    return Regex(""""s"\s*:\s*\{[^}]{0,300}"t"\s*:\s*"([^"]{1,})"""").containsMatchIn(json)
}
