package org.churchpresenter.cross.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.delay

/** How long a status line stays on screen before it clears itself. */
private const val STATUS_MESSAGE_MS = 3_000L

/** Clue-list marker: green when the engine placed the clue on the grid, amber when it could not. */
private val PLACED_CLUE_COLOR = Color(0xFF4CAF50)
private val UNPLACED_CLUE_COLOR = Color(0xFFFFA500)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState()
    ) {
        content()
    }
}

@Composable
fun AdminApp() {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { AdminState(scope) }

    LaunchedEffect(Unit) {
        PUZZLES_DIR.mkdirs()
        ENCODED_DIR.mkdirs()
        state.refreshLevels()
        state.levels.firstOrNull()?.let { state.loadLevel(it) }
    }

    // The folder is edited from outside this window too, so it is re-read rather than assumed.
    LaunchedEffect(Unit) {
        while (true) {
            delay(STATUS_MESSAGE_MS)
            state.refreshLevels()
        }
    }

    CrossTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                LevelHeader(state)
                HorizontalDivider()
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    EditorPane(state, modifier = Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider()
                    CrosswordPreview(
                        clues = state.parsed?.third ?: emptyList(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                HorizontalDivider()
                ActionBar(state)
            }
        }
    }
}

@Composable
private fun LevelHeader(state: AdminState) {
    val index = state.levels.indexOf(state.currentLevel)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tip("Go to previous level") {
            IconButton(
                onClick = { state.loadLevel(state.levels[index - 1]) },
                enabled = index > 0
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.navPrev) }
        }

        Text(
            text = if (state.levels.isEmpty()) "No levels"
            else "Level ${state.currentLevel}  (${index + 1} / ${state.levels.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Tip("Go to next level") {
            IconButton(
                onClick = { state.loadLevel(state.levels[index + 1]) },
                enabled = index < state.levels.size - 1
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = Strings.navNext) }
        }

        Spacer(Modifier.width(16.dp))

        Tip("Create a new empty level and open it for editing") {
            OutlinedButton(onClick = { state.newLevel() }) { Text(Strings.newLevel) }
        }

        Spacer(Modifier.width(16.dp))

        OutlinedTextField(
            value = state.jumpText,
            onValueChange = { entered -> state.jumpText = entered.filter { it.isDigit() } },
            label = { Text(Strings.jumpTo) },
            modifier = Modifier.width(100.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { state.jumpToLevel() })
        )

        UnexportedWarnings(state)
    }
}

@Composable
private fun UnexportedWarnings(state: AdminState) {
    val templates = (state.unexportedLevels intersect state.templateLevels).sorted()
    val unexported = (state.unexportedLevels - state.templateLevels).sorted()
    if (templates.isNotEmpty()) {
        Spacer(Modifier.width(16.dp))
        Text(
            text = "⚠ Level ${templates.joinToString(", ")}: clues not yet entered",
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.labelMedium
        )
    }
    if (unexported.isNotEmpty()) {
        Spacer(Modifier.width(16.dp))
        Text(
            text = "⚠ Level ${unexported.joinToString(", ")} not exported",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun EditorPane(state: AdminState, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        val editorStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        OutlinedTextField(
            value = state.rawText,
            onValueChange = { state.edit(it) },
            modifier = Modifier.fillMaxSize().padding(12.dp).padding(start = 18.dp),
            textStyle = editorStyle,
            colors = TextFieldDefaults.colors(),
            placeholder = { Text(Strings.editorPlaceholder, fontFamily = FontFamily.Monospace) }
        )
        ClueGutter(state, editorStyle)
    }
}

/**
 * The dot beside each clue line saying whether the engine could place it.
 *
 * Two lines are measured and `getLineTop(1)` taken, which is the inter-line spacing as it really
 * falls in a multiline field — the font's own line height is not the same number.
 */
@Composable
private fun BoxScope.ClueGutter(state: AdminState, editorStyle: TextStyle) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val lineHeightDp = remember(textMeasurer, density) {
        with(density) { textMeasurer.measure("A\nB", editorStyle).getLineTop(1).toDp() }
    }
    // 12dp outer padding + 16dp M3 content inset + 1dp border stroke
    Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 29.dp, start = 14.dp)) {
        state.lineStatuses.forEach { placed ->
            Box(modifier = Modifier.height(lineHeightDp).width(16.dp)) {
                if (placed != null) {
                    Text(
                        text = if (placed) "●" else "⚠",
                        fontSize = 9.sp,
                        color = if (placed) PLACED_CLUE_COLOR else UNPLACED_CLUE_COLOR,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBar(state: AdminState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tip("Import a .txt plaintext puzzle file from disk into this level") {
            OutlinedButton(onClick = {
                openFileDialog("Load Plaintext", listOf("txt"))?.let { file ->
                    state.edit(file.readText(Charsets.UTF_8))
                    state.say("Imported ${file.name}")
                }
            }) { Text(Strings.importTxt) }
        }

        Spacer(Modifier.width(8.dp))

        Tip("Decode and load the saved .xwp file for this level back into the editor") {
            OutlinedButton(onClick = { state.loadEncoded() }) { Text(Strings.loadXwp) }
        }

        Spacer(Modifier.width(8.dp))

        Tip("Encode and save this level as a .xwp file for use in the presenter") {
            Button(onClick = { state.exportCurrent() }) { Text(Strings.exportXwp) }
        }

        Spacer(Modifier.width(8.dp))

        Tip(
            "Build the crossword grid and renumber clues in reading order " +
                "(top→bottom, left→right). Also converts simplified formats to " +
                "standard layout."
        ) {
            OutlinedButton(onClick = { state.fixReorder() }) { Text(Strings.fixReorder) }
        }

        Spacer(Modifier.width(16.dp))

        Tip("Export all levels to .xwp files in one go, skipping any that are invalid or still unedited") {
            OutlinedButton(onClick = {
                state.say(exportAllLevels())
                state.refreshLevels()
            }) { Text(Strings.exportAll) }
        }

        Spacer(Modifier.width(8.dp))

        Tip("Decode all .xwp files in the encoded/ folder back to plaintext .txt files") {
            OutlinedButton(onClick = {
                state.say(decodeAllLevels())
                state.refreshLevels()
            }) { Text(Strings.decodeAll) }
        }

        Spacer(Modifier.weight(1f))

        state.status?.let { message ->
            Text(
                text = message,
                color = if (state.statusIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun openFileDialog(title: String, extensions: List<String>): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.file = extensions.joinToString(";") { "*.$it" }
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(dir, file)
}
