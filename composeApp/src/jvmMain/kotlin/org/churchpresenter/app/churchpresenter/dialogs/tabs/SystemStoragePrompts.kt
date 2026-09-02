package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.conversion_complete
import churchpresenter.composeapp.generated.resources.conversion_complete_message
import churchpresenter.composeapp.generated.resources.conversion_complete_with_errors
import churchpresenter.composeapp.generated.resources.folder_already_exists
import churchpresenter.composeapp.generated.resources.folder_overwrite_confirm
import churchpresenter.composeapp.generated.resources.song_samples
import churchpresenter.composeapp.generated.resources.song_samples_copied
import churchpresenter.composeapp.generated.resources.song_samples_overwrite_confirm
import org.churchpresenter.app.churchpresenter.data.ConversionResult
import org.churchpresenter.app.churchpresenter.data.SpsConverter
import org.jetbrains.compose.resources.stringResource
import javax.swing.JOptionPane

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

private const val SONG_SAMPLES_FOLDER = "Song Samples"

internal suspend fun copySongSamples(storageDirectory: String): Int {
    val targetDir = java.io.File(storageDirectory, SONG_SAMPLES_FOLDER)
    if (!targetDir.exists()) targetDir.mkdirs()

    val indexBytes = Res.readBytes("files/song_samples/index.txt")
    val filenames = indexBytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }

    var count = 0
    for (filename in filenames) {
        try {
            val songBytes = Res.readBytes("files/song_samples/$filename")
            java.io.File(targetDir, filename).writeBytes(songBytes)
            count++
        } catch (_: Exception) {
            // Skip files that can't be read
        }
    }
    return count
}
