package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.composables.CopyLinkIconButton
import org.churchpresenter.app.churchpresenter.composables.FfmpegBinary
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.SystemClipboard
import org.churchpresenter.app.churchpresenter.utils.UrlOpener
import org.churchpresenter.settings.AppSettings
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ffmpeg_browse
import churchpresenter.composeapp.generated.resources.ffmpeg_bundled_ready
import churchpresenter.composeapp.generated.resources.ffmpeg_check_again
import churchpresenter.composeapp.generated.resources.ffmpeg_clear
import churchpresenter.composeapp.generated.resources.ffmpeg_download_link
import churchpresenter.composeapp.generated.resources.ffmpeg_missing_help
import churchpresenter.composeapp.generated.resources.ffmpeg_missing_title
import churchpresenter.composeapp.generated.resources.ffmpeg_path_help
import churchpresenter.composeapp.generated.resources.ffmpeg_path_hint
import churchpresenter.composeapp.generated.resources.ffmpeg_ready
import churchpresenter.composeapp.generated.resources.ffmpeg_section_title
import org.jetbrains.compose.resources.stringResource
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/** Where an operator who wants their own build gets one. Only shown when we could not find any. */
internal const val FFMPEG_DOWNLOAD_URL = "https://ffmpeg.org/download.html"

private val PATH_FIELD_WIDTH = 320.dp

/**
 * What the app found when it went looking for ffmpeg.
 *
 * Three states rather than a boolean, because "found ours", "found yours" and "found none" call for
 * three different sentences — and the middle one is not a fault, it is the override working.
 */
data class FfmpegStatus(
    val available: Boolean,
    val path: String,
    val bundled: Boolean,
)

/** What was resolved last time, without asking the machine again. Cheap, but still not free. */
fun probeFfmpeg(): FfmpegStatus =
    FfmpegStatus(FfmpegBinary.isAvailable, FfmpegBinary.path, FfmpegBinary.isBundled)

/**
 * Applies [customPath] as the override — empty for "use the bundled one" — and resolves again.
 *
 * Both of this card's actions go through it, "Check again" by re-applying the path already saved:
 * asking the machine again is the same operation as changing which binary to ask about. It runs
 * `ffmpeg -version`, so it belongs on `Dispatchers.IO` and never on a click handler.
 */
fun applyFfmpegPath(customPath: String): FfmpegStatus {
    FfmpegBinary.customPath = customPath
    FfmpegBinary.recheck()
    return probeFfmpeg()
}

/**
 * The Camera Capture card: which ffmpeg is in use, and how to use a different one.
 *
 * The app ships its own ffmpeg, so unlike the VLC and NDI cards beside it this one has nothing to
 * ask the operator to install in the ordinary case — it exists to *say which binary is being used*
 * and to let that be overridden, and only offers a download link when the search came up empty.
 *
 * [onProbe], [onApply], [openUrl] and [copyText] are defaulted parameters rather than direct calls so a test
 * can describe a machine and capture what was opened, instead of launching a browser, taking the
 * clipboard, or depending on whether the machine it runs on has ffmpeg installed. That is the seam
 * `VlcStep` and `NdiOutputsCard` already use.
 */
@Composable
internal fun FfmpegCard(
    settings: AppSettings,
    onSettingsChange: (((AppSettings) -> AppSettings) -> Unit),
    onProbe: () -> FfmpegStatus = ::probeFfmpeg,
    onApply: (String) -> FfmpegStatus = ::applyFfmpegPath,
    openUrl: (String) -> Unit = { UrlOpener.open(it) },
    copyText: (String) -> Unit = { SystemClipboard.copy(it) },
) {
    val path = settings.projectionSettings.ffmpegPath
    var status by remember { mutableStateOf<FfmpegStatus?>(null) }

    // Null until the probe answers. It launches a process, so it cannot run in composition, and
    // "ffmpeg was not found" shown while nothing has looked yet is a wrong answer, not a pending
    // one — the same distinction `cameraHintStringRes` draws for a null device list.
    LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { onProbe() } }

    SettingsSection(title = stringResource(Res.string.ffmpeg_section_title)) {
        FfmpegStatusMessage(status)
        FfmpegPathRow(
            path = path,
            status = status,
            onSettingsChange = onSettingsChange,
            onApply = onApply,
            onStatus = { status = it },
            openUrl = openUrl,
            copyText = copyText,
        )
        Text(
            text = stringResource(Res.string.ffmpeg_path_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What [status] means, in the operator's words. Nothing is said until the probe has answered. */
@Composable
private fun FfmpegStatusMessage(status: FfmpegStatus?) {
    if (status == null) return
    when {
        status.available && status.bundled -> Text(
            text = stringResource(Res.string.ffmpeg_bundled_ready),
            style = MaterialTheme.typography.bodyMedium,
        )

        status.available -> Text(
            text = stringResource(Res.string.ffmpeg_ready, status.path),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        else -> {
            Text(
                text = stringResource(Res.string.ffmpeg_missing_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(Res.string.ffmpeg_missing_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The override: the chosen path, a picker, a re-probe, and — only when nothing was found — a link.
 *
 * The field is read-only and filled by the picker, exactly as the VLC and NDI path fields on this
 * same tab are: a path typed halfway is a path that does not exist, and this one names a program
 * rather than a folder, so it is picked as a file.
 */
@Composable
@Suppress("LongParameterList")  // One row's worth of state and callbacks, split out of FfmpegCard.
private fun FfmpegPathRow(
    path: String,
    status: FfmpegStatus?,
    onSettingsChange: (((AppSettings) -> AppSettings) -> Unit),
    onApply: (String) -> FfmpegStatus,
    onStatus: (FfmpegStatus) -> Unit,
    openUrl: (String) -> Unit,
    copyText: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(Res.string.ffmpeg_path_hint)

    /** Saves [selected] as the override, applies it, and re-probes — the only way this row changes. */
    fun applyPath(selected: String) {
        onSettingsChange { s ->
            s.copy(projectionSettings = s.projectionSettings.copy(ffmpegPath = selected))
        }
        scope.launch { onStatus(withContext(Dispatchers.IO) { onApply(selected) }) }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextField(
            value = path,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(Res.string.ffmpeg_path_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.width(PATH_FIELD_WIDTH),
        )
        OutlinedButton(
            shape = RoundedCornerShape(6.dp),
            onClick = {
                scope.launch {
                    val chosen = FileChooser.platformInstance.chooseSingle(
                        path = path.takeIf { it.isNotBlank() }?.let(::Path),
                        title = chooserTitle,
                        selectDirectory = false,
                        filters = emptyList(),
                    ) ?: return@launch
                    applyPath(chosen.absolutePathString())
                }
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(stringResource(Res.string.ffmpeg_browse), style = MaterialTheme.typography.labelSmall)
        }
        if (path.isNotBlank()) {
            OutlinedButton(
                shape = RoundedCornerShape(6.dp),
                onClick = { applyPath("") },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.ffmpeg_clear), style = MaterialTheme.typography.labelSmall)
            }
        }
        OutlinedButton(
            shape = RoundedCornerShape(6.dp),
            // Off the UI thread: this launches `ffmpeg -version`, and on the click handler it
            // would freeze the dialog for as long as that takes to answer.
            onClick = { scope.launch { onStatus(withContext(Dispatchers.IO) { onApply(path) }) } },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(stringResource(Res.string.ffmpeg_check_again), style = MaterialTheme.typography.labelSmall)
        }
        if (status?.available == false) {
            Button(
                shape = RoundedCornerShape(6.dp),
                onClick = { openUrl(FFMPEG_DOWNLOAD_URL) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.ffmpeg_download_link), style = MaterialTheme.typography.labelSmall)
            }
            CopyLinkIconButton(url = FFMPEG_DOWNLOAD_URL, onCopy = copyText)
        }
    }
}
