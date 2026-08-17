package org.churchpresenter.cross.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.cross.data.ClueEntry
import org.churchpresenter.cross.data.CrosswordEngine
import org.churchpresenter.cross.data.Direction
import org.churchpresenter.cross.data.decode
import org.churchpresenter.cross.data.encode
import org.churchpresenter.cross.data.fromPlaintext
import org.churchpresenter.cross.data.fromPlaintextSimple
import org.churchpresenter.cross.data.toPlaintext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private val PUZZLES_DIR = File("puzzles")
private val ENCODED_DIR = File("encoded")

private fun templateFor(level: Int) = """
# Level $level
ACROSS:
1. Clue text here | ANSWER
DOWN:
2. Another clue | WORD
""".trimIndent()

private val PLACEHOLDER_ANSWERS = setOf("ANSWER", "WORD")

private fun isUnedited(text: String): Boolean {
    val parsed = fromPlaintext(text) ?: return false
    return parsed.third.any { it.answer in PLACEHOLDER_ANSWERS }
}

private fun detectLevels(): List<Int> =
    PUZZLES_DIR.listFiles { f -> f.name.matches(Regex("level(\\d+)\\.txt")) }
        ?.mapNotNull { Regex("level(\\d+)\\.txt").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
        ?.sorted() ?: emptyList()

private fun nextNewLevel(levels: List<Int>): Int =
    (levels.maxOrNull()?.plus(1)) ?: 0

private fun txtNewerThanXwp(level: Int): Boolean {
    val txt = File(PUZZLES_DIR, "level$level.txt")
    val xwp = File(ENCODED_DIR, "level$level.xwp")
    if (!txt.exists()) return false
    if (!xwp.exists()) return true
    if (txt.lastModified() <= xwp.lastModified()) return false
    // Timestamp says txt is newer — only warn if content actually differs
    return try {
        txt.readText(Charsets.UTF_8).trim() != decode(xwp.readText(Charsets.UTF_8)).trim()
    } catch (_: Exception) {
        true
    }
}

private fun scanUnexported(levels: List<Int>): Set<Int> =
    levels.filter { txtNewerThanXwp(it) }.toSet()

private fun scanTemplateLevels(levels: List<Int>): Set<Int> =
    levels.filter { level ->
        val file = File(PUZZLES_DIR, "level$level.txt")
        file.exists() && try { isUnedited(file.readText(Charsets.UTF_8)) } catch (_: Exception) { false }
    }.toSet()

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
    val autoSaveJob = remember { mutableStateOf<Job?>(null) }

    var levels by remember { mutableStateOf(emptyList<Int>()) }
    var currentLevel by remember { mutableStateOf(0) }
    var jumpText by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf(templateFor(0)) }

    // Set of all levels whose .txt is newer than (or has no) .xwp
    var unexportedLevels by remember { mutableStateOf(emptySet<Int>()) }
    // Subset of unexportedLevels where the file is still an unedited template
    var templateLevels by remember { mutableStateOf(emptySet<Int>()) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val parsed by remember { derivedStateOf { fromPlaintext(rawText) } }
    val placedNumbers by remember { derivedStateOf {
        parsed?.third?.let { CrosswordEngine.build(it)?.placedNumbers } ?: emptySet()
    } }
    // null = no indicator (blank/header/direction line), true = placed, false = not placed
    val lineStatuses by remember { derivedStateOf {
        val clueRegex = Regex("""^(\d+)\.\s*.+\|.+$""")
        rawText.lines().map { line ->
            val num = clueRegex.matchEntire(line.trim())?.groupValues?.get(1)?.toIntOrNull()
                ?: return@map null
            num in placedNumbers
        }
    } }

    fun showStatus(msg: String, error: Boolean = false) {
        statusMessage = msg
        statusIsError = error
    }

    fun refreshLevels() {
        levels = detectLevels()
        unexportedLevels = scanUnexported(levels)
        templateLevels = scanTemplateLevels(levels)
    }

    fun loadLevel(level: Int) {
        autoSaveJob.value?.cancel()
        val file = File(PUZZLES_DIR, "level$level.txt")
        rawText = if (file.exists()) file.readText(Charsets.UTF_8) else templateFor(level)
        // Recompute from disk; user edits will add currentLevel back if needed
        unexportedLevels = scanUnexported(levels)
        templateLevels = scanTemplateLevels(levels)
    }

    fun autoSave() {
        PUZZLES_DIR.mkdirs()
        File(PUZZLES_DIR, "level$currentLevel.txt").writeText(rawText, Charsets.UTF_8)
        refreshLevels()
    }

    fun exportLevel() {
        val (_, title, clues) = parsed ?: run { showStatus("Invalid puzzle format.", error = true); return }
        if (isUnedited(rawText)) { showStatus("Replace the placeholder clues before exporting.", error = true); return }
        // Auto-inject LAYOUT section if not already present so the main app uses exact positions
        val exportText = if (rawText.contains("LAYOUT:", ignoreCase = true)) {
            rawText
        } else {
            val puzzle = CrosswordEngine.build(clues) ?: run {
                showStatus("Cannot export: clues don't form a valid crossword — run Fix/Reorder first.", error = true); return
            }
            val enriched = toPlaintext(1, title, clues, puzzle.placedPositions, puzzle.placedDirections)
            rawText = enriched
            autoSaveJob.value?.cancel()
            autoSaveJob.value = scope.launch { delay(800); autoSave() }
            enriched
        }
        ENCODED_DIR.mkdirs()
        File(ENCODED_DIR, "level$currentLevel.xwp").writeText(encode(exportText), Charsets.UTF_8)
        unexportedLevels = unexportedLevels - currentLevel
        showStatus("Exported level$currentLevel.xwp")
    }

    fun loadEncodedLevel() {
        val file = File(ENCODED_DIR, "level$currentLevel.xwp")
        if (!file.exists()) { showStatus("level$currentLevel.xwp not found in encoded/", error = true); return }
        try {
            rawText = decode(file.readText(Charsets.UTF_8))
            unexportedLevels = unexportedLevels - currentLevel
            showStatus("Loaded encoded level $currentLevel")
        } catch (_: Exception) {
            showStatus("Failed to decode level$currentLevel.xwp", error = true)
        }
    }

    fun jumpToLevel() {
        val n = jumpText.toIntOrNull() ?: return
        jumpText = ""
        currentLevel = n
        if (n in levels) {
            loadLevel(n)
        } else {
            rawText = templateFor(n)
            unexportedLevels = unexportedLevels - n
            showStatus("Level $n not found — edit to create it")
        }
    }

    fun exportAll() {
        val all = detectLevels()
        if (all.isEmpty()) { showStatus("No plaintext levels found in puzzles/", error = true); return }
        ENCODED_DIR.mkdirs()
        var ok = 0; var fail = 0
        for (lvl in all) {
            val rawFile = File(PUZZLES_DIR, "level$lvl.txt")
            val txt = rawFile.readText(Charsets.UTF_8)
            val parsed = fromPlaintext(txt)
            if (parsed == null || isUnedited(txt)) { fail++; continue }
            val (_, title, clues) = parsed
            // Auto-inject LAYOUT section if not already present
            val exportText = if (txt.contains("LAYOUT:", ignoreCase = true)) {
                txt
            } else {
                val puzzle = CrosswordEngine.build(clues)
                if (puzzle == null) { fail++; continue }
                val enriched = toPlaintext(1, title, clues, puzzle.placedPositions, puzzle.placedDirections)
                rawFile.writeText(enriched, Charsets.UTF_8)
                enriched
            }
            File(ENCODED_DIR, "level$lvl.xwp").writeText(encode(exportText), Charsets.UTF_8)
            ok++
        }
        unexportedLevels = scanUnexported(levels)
        showStatus("Exported $ok level(s)${if (fail > 0) ", $fail skipped (invalid/unplaceable)" else ""}")
    }

    fun fixReorder() {
        val (_, title, clues) = fromPlaintext(rawText)
            ?: run {
                val simpleClues = fromPlaintextSimple(rawText)
                    ?: run { showStatus("Cannot reorder: invalid puzzle format.", error = true); return }
                Triple(1, "Level $currentLevel", simpleClues)
            }
        // De-duplicate clue numbers before building: if multiple clues share a number,
        // assign provisional unique numbers so the engine places each independently
        val seenNums = mutableSetOf<Int>()
        var provNext = (clues.maxOfOrNull { it.number } ?: 0) + 1
        val uniqueClues = clues.map { clue ->
            if (seenNums.add(clue.number)) clue else clue.copy(number = provNext++)
        }
        val puzzle = CrosswordEngine.build(uniqueClues) ?: run {
            showStatus("Cannot reorder: no words could be placed.", error = true); return
        }
        // Map old clue numbers → new sequential numbers via placed positions → cell number
        val renumber = uniqueClues.mapNotNull { clue ->
            val pos = puzzle.placedPositions[clue.number] ?: return@mapNotNull null
            val newNum = puzzle.grid[pos]?.clueNumber ?: return@mapNotNull null
            clue.number to newNum
        }.toMap()
        val placed = puzzle.placedNumbers
        // Unplaced clues get numbers continuing after all placed ones
        var nextNum = (renumber.values.maxOrNull() ?: 0) + 1
        val fixedClues = uniqueClues.map { clue ->
            val newNum = when {
                clue.number in renumber -> renumber[clue.number]!!
                clue.number in placed   -> nextNum++ // placed but shares a start cell
                else                    -> nextNum++ // not placed — assign number anyway
            }
            val newDir = puzzle.placedDirections[clue.number] ?: clue.direction
            clue.copy(number = newNum, direction = newDir)
        }
        // Remap positions: uniqueClues[i] → fixedClues[i], old number → new number
        val fixedPositions = fixedClues.indices.mapNotNull { i ->
            val pos = puzzle.placedPositions[uniqueClues[i].number] ?: return@mapNotNull null
            fixedClues[i].number to pos
        }.toMap()
        rawText = toPlaintext(1, title, fixedClues, fixedPositions)
        unexportedLevels = unexportedLevels + currentLevel
        autoSaveJob.value?.cancel()
        autoSaveJob.value = scope.launch { delay(800); autoSave() }
        val unplaced = uniqueClues.count { it.number !in placed }
        showStatus(if (unplaced > 0) "Reordered — $unplaced clue(s) not in puzzle" else "Reordered — all ${fixedClues.size} clues placed")
    }

    fun decodeAll() {
        val xwpFiles = ENCODED_DIR.listFiles { f -> f.name.matches(Regex("level(\\d+)\\.xwp")) }
            ?: emptyArray()
        if (xwpFiles.isEmpty()) { showStatus("No .xwp files found in encoded/", error = true); return }
        PUZZLES_DIR.mkdirs()
        var ok = 0; var fail = 0
        for (file in xwpFiles) {
            try {
                val levelNum = Regex("level(\\d+)\\.xwp").matchEntire(file.name)!!.groupValues[1]
                File(PUZZLES_DIR, "level$levelNum.txt").writeText(decode(file.readText(Charsets.UTF_8)), Charsets.UTF_8)
                ok++
            } catch (_: Exception) { fail++ }
        }
        refreshLevels()
        showStatus("Decoded $ok level(s)${if (fail > 0) ", $fail failed" else ""}")
    }

    // Init
    LaunchedEffect(Unit) {
        PUZZLES_DIR.mkdirs()
        ENCODED_DIR.mkdirs()
        val detected = detectLevels()
        levels = detected
        unexportedLevels = scanUnexported(detected)
        if (detected.isNotEmpty()) {
            currentLevel = detected.first()
            loadLevel(currentLevel)
        }
    }

    // Poll ALL levels for external file changes every 3 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            val current = detectLevels()
            if (current != levels) levels = current
            unexportedLevels = scanUnexported(current)
            templateLevels = scanTemplateLevels(current)
        }
    }

    CrossTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Level header ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Tip("Go to previous level") {
                        IconButton(
                            onClick = {
                                val idx = levels.indexOf(currentLevel)
                                if (idx > 0) { currentLevel = levels[idx - 1]; loadLevel(currentLevel) }
                            },
                            enabled = levels.indexOf(currentLevel) > 0
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.navPrev) }
                    }

                    Text(
                        text = if (levels.isEmpty()) "No levels"
                               else "Level $currentLevel  (${levels.indexOf(currentLevel) + 1} / ${levels.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Tip("Go to next level") {
                        IconButton(
                            onClick = {
                                val idx = levels.indexOf(currentLevel)
                                if (idx < levels.size - 1) { currentLevel = levels[idx + 1]; loadLevel(currentLevel) }
                            },
                            enabled = levels.indexOf(currentLevel) < levels.size - 1
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = Strings.navNext) }
                    }

                    Spacer(Modifier.width(16.dp))

                    Tip("Create a new empty level and open it for editing") {
                        OutlinedButton(onClick = {
                            val newLevel = nextNewLevel(levels)
                            currentLevel = newLevel
                            rawText = templateFor(newLevel)
                            PUZZLES_DIR.mkdirs()
                            File(PUZZLES_DIR, "level$newLevel.txt").writeText(templateFor(newLevel), Charsets.UTF_8)
                            refreshLevels()
                            showStatus("New level $newLevel — edit to begin")
                        }) { Text(Strings.newLevel) }
                    }

                    Spacer(Modifier.width(16.dp))

                    OutlinedTextField(
                        value = jumpText,
                        onValueChange = { jumpText = it.filter { c -> c.isDigit() } },
                        label = { Text(Strings.jumpTo) },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { jumpToLevel() })
                    )

                    val templateUnexported = (unexportedLevels intersect templateLevels).sorted()
                    val reallyUnexported = (unexportedLevels - templateLevels).sorted()
                    if (templateUnexported.isNotEmpty()) {
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "⚠ Level ${templateUnexported.joinToString(", ")}: clues not yet entered",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (reallyUnexported.isNotEmpty()) {
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "⚠ Level ${reallyUnexported.joinToString(", ")} not exported",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                HorizontalDivider()

                // ── Main content ──────────────────────────────────────────────
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Left half: status gutter + text editor
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val editorStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        OutlinedTextField(
                            value = rawText,
                            onValueChange = { newText ->
                                rawText = newText
                                unexportedLevels = unexportedLevels + currentLevel
                                autoSaveJob.value?.cancel()
                                autoSaveJob.value = scope.launch { delay(800); autoSave() }
                            },
                            modifier = Modifier.fillMaxSize().padding(12.dp).padding(start = 18.dp),
                            textStyle = editorStyle,
                            colors = TextFieldDefaults.colors(),
                            placeholder = { Text(Strings.editorPlaceholder, fontFamily = FontFamily.Monospace) }
                        )
                        // Per-line status gutter: measure two lines and take getLineTop(1) to get
                        // the exact inter-line spacing as used in a multiline context
                        val density = LocalDensity.current
                        val textMeasurer = rememberTextMeasurer()
                        val lineHeightDp = remember(textMeasurer, density) {
                            with(density) { textMeasurer.measure("A\nB", editorStyle).getLineTop(1).toDp() }
                        }
                        // 12dp outer padding + 16dp M3 content inset + 1dp border stroke
                        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 29.dp, start = 14.dp)) {
                            lineStatuses.forEach { placed ->
                                Box(modifier = Modifier.height(lineHeightDp).width(16.dp)) {
                                    if (placed != null) {
                                        Text(
                                            text = if (placed) "●" else "⚠",
                                            fontSize = 9.sp,
                                            color = if (placed) Color(0xFF4CAF50) else Color(0xFFFFA500),
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    VerticalDivider()

                    CrosswordPreview(
                        clues = parsed?.third ?: emptyList(),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                HorizontalDivider()

                // ── Bottom bar ────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Tip("Import a .txt plaintext puzzle file from disk into this level") {
                        OutlinedButton(onClick = {
                            val file = openFileDialog("Load Plaintext", listOf("txt")) ?: return@OutlinedButton
                            rawText = file.readText(Charsets.UTF_8)
                            unexportedLevels = unexportedLevels + currentLevel
                            autoSaveJob.value?.cancel()
                            autoSaveJob.value = scope.launch { delay(800); autoSave() }
                            showStatus("Imported ${file.name}")
                        }) { Text(Strings.importTxt) }
                    }

                    Spacer(Modifier.width(8.dp))

                    Tip("Decode and load the saved .xwp file for this level back into the editor") {
                        OutlinedButton(onClick = { loadEncodedLevel() }) { Text(Strings.loadXwp) }
                    }

                    Spacer(Modifier.width(8.dp))

                    Tip("Encode and save this level as a .xwp file for use in the presenter") {
                        Button(onClick = { exportLevel() }) { Text(Strings.exportXwp) }
                    }

                    Spacer(Modifier.width(8.dp))

                    Tip("Build the crossword grid and renumber clues in reading order (top→bottom, left→right). Also converts simplified formats to standard layout.") {
                        OutlinedButton(onClick = { fixReorder() }) { Text(Strings.fixReorder) }
                    }

                    Spacer(Modifier.width(16.dp))

                    Tip("Export all levels to .xwp files in one go, skipping any that are invalid or still unedited") {
                        OutlinedButton(onClick = { exportAll() }) { Text(Strings.exportAll) }
                    }

                    Spacer(Modifier.width(8.dp))

                    Tip("Decode all .xwp files in the encoded/ folder back to plaintext .txt files") {
                        OutlinedButton(onClick = { decodeAll() }) { Text(Strings.decodeAll) }
                    }

                    Spacer(Modifier.weight(1f))

                    statusMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = if (statusIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
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
