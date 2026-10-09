package org.churchpresenter.appsettings

import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.conversion_complete
import org.churchpresenter.strings.generated.resources.conversion_complete_message
import org.churchpresenter.strings.generated.resources.conversion_complete_with_errors
import org.churchpresenter.strings.generated.resources.folder_already_exists
import org.churchpresenter.strings.generated.resources.folder_overwrite_confirm
import org.churchpresenter.strings.generated.resources.song_samples
import org.churchpresenter.strings.generated.resources.song_samples_copied
import org.churchpresenter.strings.generated.resources.song_samples_overwrite_confirm
import org.jetbrains.compose.resources.stringResource
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/** The dialogs the sample copy puts up, with their wording resolved while still in composition. */
internal class SongSamplePrompts(
    private val folderExistsTitle: String,
    private val overwriteMessage: String,
    private val samplesTitle: String,
    private val copiedFormat: String,
) {
    fun confirmOverwrite(directory: String): Boolean {
        val samplesDir = java.io.File(directory, SONG_SAMPLES_FOLDER)
        if (!samplesDir.exists()) return true
        return JOptionPane.showConfirmDialog(
            null, overwriteMessage, folderExistsTitle, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
        ) == JOptionPane.YES_OPTION
    }

    fun reportCopied(count: Int) {
        JOptionPane.showMessageDialog(
            null, String.format(copiedFormat, count), samplesTitle, JOptionPane.INFORMATION_MESSAGE,
        )
    }
}

@Composable
internal fun songSamplePrompts() = SongSamplePrompts(
    folderExistsTitle = stringResource(Res.string.folder_already_exists),
    overwriteMessage = stringResource(Res.string.song_samples_overwrite_confirm),
    samplesTitle = stringResource(Res.string.song_samples),
    copiedFormat = stringResource(Res.string.song_samples_copied),
)

/** The same, for a `.sps` conversion. */
internal class ConversionPrompts(
    private val folderExistsTitle: String,
    private val overwriteFormat: String,
    private val completeTitle: String,
    private val completeFormat: String,
    private val errorsFormat: String,
) {
    fun confirmOverwrite(directory: String, fileName: String): Boolean {
        val converter = SpsConverter()
        val spsPath = java.io.File(directory, fileName).absolutePath
        if (!converter.targetFolderExists(spsPath, directory)) return true
        val folderName = converter.getTargetFolderName(spsPath) ?: fileName
        return JOptionPane.showConfirmDialog(
            null,
            String.format(overwriteFormat, folderName),
            folderExistsTitle,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        ) == JOptionPane.YES_OPTION
    }

    fun report(result: ConversionResult) {
        if (result.errors.isEmpty()) {
            JOptionPane.showMessageDialog(
                null,
                String.format(completeFormat, result.songsConverted, java.io.File(result.songbookFolder).name),
                completeTitle,
                JOptionPane.INFORMATION_MESSAGE,
            )
        } else {
            JOptionPane.showMessageDialog(
                null,
                String.format(errorsFormat, result.songsConverted, result.errors.joinToString("\n")),
                completeTitle,
                JOptionPane.WARNING_MESSAGE,
            )
        }
    }
}

@Composable
internal fun conversionPrompts() = ConversionPrompts(
    folderExistsTitle = stringResource(Res.string.folder_already_exists),
    overwriteFormat = stringResource(Res.string.folder_overwrite_confirm),
    completeTitle = stringResource(Res.string.conversion_complete),
    completeFormat = stringResource(Res.string.conversion_complete_message),
    errorsFormat = stringResource(Res.string.conversion_complete_with_errors),
)

/**
 * Runs [block] on a later turn of the event queue.
 *
 * The prompts above are blocking modal dialogs, and they open from a coroutine that Compose is
 * running inside its own dispatcher's flush. A modal dialog pumps events until it closes, and one
 * of those events re-enters that same dispatcher mid-run — a continuation resumed with the wrong
 * state, which surfaced as `ClassCastException: Symbol cannot be cast to Number`
 * (Sentry CHURCH-PRESENTER-DESKTOP-7E and -29). Deferring lets the flush finish first.
 */
internal fun afterDispatch(block: () -> Unit) = SwingUtilities.invokeLater(block)

private const val SONG_SAMPLES_FOLDER = "Song Samples"

/** One file of the bundled sample songs, from this module's resources. */
private fun songSample(name: String): ByteArray =
    checkNotNull(SongSamples::class.java.getResourceAsStream("/$SONG_SAMPLES_RESOURCES/$name")) {
        "no bundled sample song $name"
    }.use { it.readBytes() }

private object SongSamples

private const val SONG_SAMPLES_RESOURCES = "song_samples"

internal suspend fun copySongSamples(storageDirectory: String): Int {
    val targetDir = java.io.File(storageDirectory, SONG_SAMPLES_FOLDER)
    if (!targetDir.exists()) targetDir.mkdirs()

    val indexBytes = songSample("index.txt")
    val filenames = indexBytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }

    var count = 0
    for (filename in filenames) {
        try {
            val songBytes = songSample(filename)
            java.io.File(targetDir, filename).writeBytes(songBytes)
            count++
        } catch (_: Exception) {
            // Skip files that can't be read
        }
    }
    return count
}
