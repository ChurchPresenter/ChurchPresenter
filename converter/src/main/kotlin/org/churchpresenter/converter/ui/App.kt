package org.churchpresenter.converter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.churchpresenter.converter.song.findFormatInputs
import org.churchpresenter.converter.song.matchesFormat
import org.churchpresenter.converter.song.SongFormatConverter
import org.churchpresenter.converter.song.SongPreviewInfo
import org.churchpresenter.converter.song.SpsToSongConverter
import org.churchpresenter.bibleformats.XmlToSpbConverter
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

internal val ButtonShape = AppShape(6.dp)

/** Cards and forms stop widening past this — on a wide window a full-bleed row reads as empty band. */
internal val CONTENT_MAX_WIDTH = 820.dp

/**
 * Which tab the converter opens on.
 *
 * Named because callers outside this module choose one: the setup wizard's song step sends the user
 * straight to [SONGS], and landing them on Bibles instead reads as the button having missed.
 */
object ConverterTab {
    const val BIBLES = 0
    const val SONGS = 1
    const val DUPLICATES = 2
    const val RENAME = 3
}

@Composable
fun App(initialTab: Int = ConverterTab.BIBLES, onConverted: (sourceId: String) -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val tabs = listOf(Strings.tabBibles, Strings.tabSongs, Strings.tabDuplicates, Strings.tabRename)

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ConverterTabRow(tabs, selectedTab) { selectedTab = it }

            when (selectedTab) {
                0 -> BibleConverterTab(onConverted)
                1 -> SongsTab(onConverted)
                2 -> DuplicateFinderTab()
                3 -> BulkRenameTab()
            }
        }
    }
}

enum class ConvertState { SELECT, PREVIEW, CONVERTING, DONE }

const val BIBLE_CONVERSION = "bible"

internal fun List<String>.anyConverted(): Boolean = any { it.startsWith("OK:") }

// =============================================================================
// Shared UI components
// =============================================================================

data class PreviewItem(
    val inputName: String,
    val inputPath: String,
    val outputName: String,
    val outputPath: String,
    val details: String,
    val willOverwrite: Boolean
)

data class SpsPreviewData(
    val songbookName: String,
    val songCount: Int,
    val outputFolder: String,
    val folderExists: Boolean,
    val sampleTitles: List<String>,
    val error: String? = null
)

@Composable
internal fun PreviewRow(item: PreviewItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.inputName, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(item.outputName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (item.details.isNotBlank()) {
                Text(item.details, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
            }
            if (item.willOverwrite) {
                Text(Strings.outputOverwrite, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
            }
            Text(item.outputPath, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
        }
    }
}

@Composable
internal fun LogLine(msg: String) {
    val color = if (msg.startsWith("ERROR")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Text(msg, style = MaterialTheme.typography.bodySmall, color = color)
}

// =============================================================================
// Preview builders
// =============================================================================


/** Words the converter layer's structured preview data, which carries no strings of its own. */
private fun describePreview(info: SongPreviewInfo): String {
    val parts = mutableListOf<String>()
    if (info.title.isNotBlank()) parts.add("\"${info.title}\"")
    if (info.sectionCount > 0) parts.add(Strings.sectionCount(info.sectionCount))
    if (info.songCount > 0) parts.add(Strings.songCount(info.songCount))
    if (info.verseOrder.isNotEmpty()) parts.add(Strings.verseOrderPrefix(info.verseOrder.joinToString(", ")))
    return parts.joinToString(" | ")
}

internal fun buildFormatPreview(
    format: SongFormatConverter,
    files: List<File>,
    outputDir: File?
): List<PreviewItem> {
    return files.map { file ->
        val outDir = outputDir ?: file.parentFile
        val outFile = File(outDir, format.outputNameFor(file))
        val details = try {
            describePreview(format.describe(file))
        } catch (e: Exception) {
            Strings.parseError(e.message.orEmpty())
        }
        PreviewItem(file.name, file.absolutePath, outFile.name, outFile.absolutePath, details, outFile.exists())
    }
}

internal fun buildSpsPreview(spsFile: File, outputDir: File): SpsPreviewData {
    return try {
        val result = SpsToSongConverter.parse(spsFile)
        val folderName = SpsToSongConverter.getTargetFolderName(spsFile)
        val targetFolder = File(outputDir, folderName)
        val titles = result.songs.map { "${it.number.padStart(4, '0')} - ${it.title}" }
        SpsPreviewData(result.songbookName, result.songs.size, targetFolder.absolutePath, targetFolder.exists(), titles)
    } catch (e: Exception) {
        SpsPreviewData("", 0, "", false, emptyList(), error = e.message)
    }
}


internal fun buildBiblePreview(files: List<File>, outputDir: File?): List<PreviewItem> {
    return files.map { file ->
        val outDir = outputDir ?: file.parentFile
        val outFile = File(outDir, file.nameWithoutExtension + ".spb")
        val details = try {
            val bible = XmlToSpbConverter.parse(file)
            val parts = mutableListOf<String>()
            parts.add("\"${bible.name}\"")
            parts.add("${bible.books.size} book(s)")
            val totalVerses = bible.books.sumOf { b -> b.chapters.sumOf { c -> c.verses.size } }
            parts.add("$totalVerses verses")
            if (bible.language != null) parts.add("lang: ${bible.language}")
            parts.joinToString(" | ")
        } catch (e: Exception) { "Parse error: ${e.message}" }
        PreviewItem(file.name, file.absolutePath, outFile.name, outFile.absolutePath, details, outFile.exists())
    }
}

// =============================================================================
// File pickers
// =============================================================================

private val defaultDir: File = File(System.getProperty("user.home"), "Downloads")

/** File-chooser filter label: the product name and its extension, both untranslated by nature. */
private fun pickerLabel(source: SongSource): String = "${source.name} (${source.ext})"

/**
 * Whether the person approved the chooser [show] opened.
 *
 * The Windows look and feel's file pane can throw a NullPointerException from inside the dialog's
 * own event loop -- it repaints the list selection on a focus change and finds no cell there
 * (CHURCH-PRESENTER-DESKTOP-9R). That takes the dialog down with it; the pick is treated as
 * cancelled, so the button can simply be pressed again instead of the converter crashing.
 */
@Suppress("TooGenericExceptionCaught")
internal fun approved(show: () -> Int): Boolean = try {
    show() == JFileChooser.APPROVE_OPTION
} catch (_: NullPointerException) {
    false
}

internal fun pickFiles(description: String, vararg extensions: String, multiSelection: Boolean): List<File> {
    val chooser = JFileChooser(defaultDir).apply {
        fileFilter = FileNameExtensionFilter(description, *extensions)
        isMultiSelectionEnabled = multiSelection
        dialogTitle = Strings.selectDialog(description)
    }
    return if (approved { chooser.showOpenDialog(null) }) {
        if (multiSelection) chooser.selectedFiles.toList() else listOfNotNull(chooser.selectedFile)
    } else emptyList()
}

internal fun pickDirectory(): File? {
    val chooser = JFileChooser(defaultDir).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = Strings.selectFolder
    }
    return if (approved { chooser.showOpenDialog(null) }) chooser.selectedFile else null
}

internal fun findXmlFilesRecursive(dir: File): List<File> = findFilesRecursive(dir, "xml")

private fun findFilesRecursive(dir: File, extension: String): List<File> =
    dir.walkTopDown()
        .filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
        .sortedBy { it.absolutePath }
        .toList()


/**
 * The file picker for one song format.
 *
 * Formats whose files usually carry no extension — OpenSong writes a bare name — get a filter that
 * accepts those too, because an extension filter hides every one of their songs and the panel then
 * looks like it works while converting nothing.
 *
 * One known gap: a `.key` saved as a **package directory** rather than a zip is entered by this
 * chooser rather than selected, because the filter accepts every directory so the tree stays
 * navigable. Keynote writes the zip form by default, and "select folder" takes either — see
 * [findFormatInputs] — so the remedy is to point at the enclosing folder. Making bundles
 * selectable here means a non-traversable `FileSystemView`, which would change the chooser for
 * every format on this screen.
 */
internal fun pickSourceFiles(source: SongSource, format: SongFormatConverter): List<File> {
    val label = pickerLabel(source)
    val chooser = JFileChooser(defaultDir).apply {
        fileFilter = object : FileFilter() {
            override fun accept(file: File): Boolean = file.isDirectory || matchesFormat(file, format)
            override fun getDescription(): String = label
        }
        isMultiSelectionEnabled = format.allowsMultipleFiles
        dialogTitle = Strings.selectDialog(label)
    }
    return if (approved { chooser.showOpenDialog(null) }) {
        if (format.allowsMultipleFiles) chooser.selectedFiles.toList() else listOfNotNull(chooser.selectedFile)
    } else emptyList()
}

/** How many of the chosen files the "source files" card lists before it stops. */
internal const val FILE_LIST_LIMIT = 200

internal fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.ROOT, "%d KB", bytes / 1024)
    else -> String.format(Locale.ROOT, "%d B", bytes)
}

// =============================================================================
// Diff engine
// =============================================================================

enum class DiffType { SAME, ADD, DEL }

data class SideBySideRow(
    val leftNum: Int? = null,
    val leftText: String? = null,
    val leftType: DiffType = DiffType.SAME,
    val rightNum: Int? = null,
    val rightText: String? = null,
    val rightType: DiffType = DiffType.SAME
)

/** LCS-based side-by-side diff with aligned matching lines. */
internal fun computeSideBySide(leftLines: List<String>, rightLines: List<String>): List<SideBySideRow> {
    val n = leftLines.size
    val m = rightLines.size

    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) {
        for (j in 1..m) {
            dp[i][j] = if (leftLines[i - 1] == rightLines[j - 1]) dp[i - 1][j - 1] + 1
            else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
    }

    // Backtrack to get edit operations
    data class Op(val type: DiffType, val text: String, val li: Int, val ri: Int)
    val ops = mutableListOf<Op>()
    var i = n; var j = m
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && leftLines[i - 1] == rightLines[j - 1] -> {
                ops.add(Op(DiffType.SAME, leftLines[i - 1], i, j)); i--; j--
            }
            j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                ops.add(Op(DiffType.ADD, rightLines[j - 1], 0, j)); j--
            }
            else -> {
                ops.add(Op(DiffType.DEL, leftLines[i - 1], i, 0)); i--
            }
        }
    }
    ops.reverse()

    // Convert to side-by-side rows, pairing adjacent DEL+ADD as modifications
    val rows = mutableListOf<SideBySideRow>()
    var idx = 0
    while (idx < ops.size) {
        val op = ops[idx]
        when (op.type) {
            DiffType.SAME -> {
                rows.add(SideBySideRow(op.li, op.text, DiffType.SAME, op.ri, op.text, DiffType.SAME))
                idx++
            }
            DiffType.DEL -> {
                // Collect consecutive DELs and ADDs to pair them
                val dels = mutableListOf<Op>()
                while (idx < ops.size && ops[idx].type == DiffType.DEL) { dels.add(ops[idx]); idx++ }
                val adds = mutableListOf<Op>()
                while (idx < ops.size && ops[idx].type == DiffType.ADD) { adds.add(ops[idx]); idx++ }
                val maxLen = maxOf(dels.size, adds.size)
                for (k in 0 until maxLen) {
                    val d = dels.getOrNull(k)
                    val a = adds.getOrNull(k)
                    rows.add(SideBySideRow(
                        leftNum = d?.li, leftText = d?.text, leftType = if (d != null) DiffType.DEL else DiffType.SAME,
                        rightNum = a?.ri, rightText = a?.text, rightType = if (a != null) DiffType.ADD else DiffType.SAME
                    ))
                }
            }
            DiffType.ADD -> {
                rows.add(SideBySideRow(rightNum = op.ri, rightText = op.text, rightType = DiffType.ADD))
                idx++
            }
        }
    }
    return rows
}
