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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.lower_third_folder_hint
import churchpresenter.composeapp.generated.resources.lower_third_no_files
import churchpresenter.composeapp.generated.resources.play
import churchpresenter.composeapp.generated.resources.replace
import churchpresenter.composeapp.generated.resources.search
import churchpresenter.composeapp.generated.resources.streaming_settings
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun StreamingTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings
) {
    val folder = appSettings.streamingSettings.lowerThirdFolder

    // Scan folder for .json files
    val jsonFiles = remember(folder) {
        if (folder.isBlank()) emptyList()
        else File(folder)
            .takeIf { it.exists() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    var selectedFile by remember(jsonFiles) {
        mutableStateOf(jsonFiles.firstOrNull())
    }

    // Whether the animation is actively playing
    var isPlaying by remember { mutableStateOf(false) }

    // Search / replace pairs applied to the JSON before parsing
    var search1 by remember { mutableStateOf("") }
    var replace1 by remember { mutableStateOf("") }
    var search2 by remember { mutableStateOf("") }
    var replace2 by remember { mutableStateOf("") }

    // Load the Lottie composition from selected file content, with substitutions applied
    val rawJson = remember(selectedFile) { selectedFile?.readText() ?: "" }

    // Detect whether the file uses editable text layers ("t" key with string value)
    // or pre-rendered glyph paths ("ch" key per character — cannot be text-replaced)
    val hasEditableText = remember(rawJson) { lottieHasEditableText(rawJson) }

    // Debounced values — only recompute JSON 400ms after user stops typing
    var debouncedSearch1 by remember { mutableStateOf("") }
    var debouncedReplace1 by remember { mutableStateOf("") }
    var debouncedSearch2 by remember { mutableStateOf("") }
    var debouncedReplace2 by remember { mutableStateOf("") }

    LaunchedEffect(search1, replace1) {
        delay(400)
        debouncedSearch1 = search1
        debouncedReplace1 = replace1
    }
    LaunchedEffect(search2, replace2) {
        delay(400)
        debouncedSearch2 = search2
        debouncedReplace2 = replace2
    }

    val jsonContent = remember(rawJson, debouncedSearch1, debouncedReplace1, debouncedSearch2, debouncedReplace2) {
        var result = rawJson
        if (debouncedSearch1.isNotBlank()) result = replaceLottieText(result, debouncedSearch1, debouncedReplace1)
        if (debouncedSearch2.isNotBlank()) result = replaceLottieText(result, debouncedSearch2, debouncedReplace2)
        result
    }

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent)
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying,
        iterations = Compottie.IterateForever
    )

    // Stop when animation ends (if not looping — handled by iterations above)
    LaunchedEffect(progress) {
        if (progress >= 1f && isPlaying) {
            isPlaying = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = stringResource(Res.string.streaming_settings),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // Controls row: dropdown + play button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (jsonFiles.isEmpty()) {
                Text(
                    text = if (folder.isBlank())
                        stringResource(Res.string.lower_third_folder_hint)
                    else
                        stringResource(Res.string.lower_third_no_files),
                    style = MaterialTheme.typography.bodyMedium,
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

            // Play / Pause toggle
            IconButton(
                onClick = { if (composition != null) isPlaying = !isPlaying },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (composition != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play),
                    contentDescription = stringResource(Res.string.play),
                    modifier = Modifier.size(22.dp),
                    tint = if (composition != null) Color.White
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }

        // Search / replace rows — always editable
        SearchReplaceRow(
            search = search1,
            replace = replace1,
            onSearchChange = { search1 = it },
            onReplaceChange = { replace1 = it }
        )
        SearchReplaceRow(
            search = search2,
            replace = replace2,
            onSearchChange = { search2 = it },
            onReplaceChange = { replace2 = it }
        )

        // Info warning only if truly no editable text found at all
        if (selectedFile != null && !hasEditableText) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚠️ No editable text found in this Lottie file. Text replacement will have no effect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Preview — black 16:9
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (composition != null) {
                Image(
                    painter = rememberLottiePainter(
                        composition = composition,
                        progress = { progress }
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "📽️",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun SearchReplaceRow(
    search: String,
    replace: String,
    onSearchChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit
) {
    val searchLabel = stringResource(Res.string.search)
    val replaceLabel = stringResource(Res.string.replace)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text(searchLabel, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "→",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        OutlinedTextField(
            value = replace,
            onValueChange = onReplaceChange,
            label = { Text(replaceLabel, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
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
    var result = json.replace(Regex(""""t"\s*:\s*"$escaped"""")) {
        """"t":"$replacement""""
    }

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
