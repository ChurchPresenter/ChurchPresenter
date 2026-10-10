package org.churchpresenter.updater

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.theme.AppShape
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.update_dialog_check_interval
import org.churchpresenter.strings.generated.resources.update_dialog_title
import org.churchpresenter.strings.generated.resources.update_dialog_up_to_date_title
import org.churchpresenter.strings.generated.resources.update_interval_every_2_months
import org.churchpresenter.strings.generated.resources.update_interval_every_3_months
import org.churchpresenter.strings.generated.resources.update_interval_every_6_months
import org.churchpresenter.strings.generated.resources.update_interval_every_launch
import org.churchpresenter.strings.generated.resources.update_interval_monthly
import org.churchpresenter.strings.generated.resources.update_interval_never
import org.churchpresenter.strings.generated.resources.update_interval_weekly
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.settings.utils.UpdateCheckInterval
import org.churchpresenter.theme.ProvideUiFontScale
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.io.OutputStream
import java.io.InputStream
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.sharedui.utils.UrlOpener

/**
 * The temp-file suffix for a downloaded installer, inferred from the release asset's URL so the
 * OS-native launcher in [launchInstaller] can dispatch on file extension.
 */
internal fun installerSuffixFor(downloadUrl: String): String = when {
    downloadUrl.endsWith(".msi", ignoreCase = true) -> ".msi"
    downloadUrl.endsWith(".dmg", ignoreCase = true) -> ".dmg"
    downloadUrl.endsWith(".deb", ignoreCase = true) -> ".deb"
    else -> ".bin"
}

/**
 * Copies [input] to [output], reporting progress as each chunk lands, and returns the bytes copied.
 *
 * Split out of the update download so it can be tested without a server: what it has to get right is
 * that **every byte arrives** — a short copy produces an installer that fails to run, minutes after
 * the operator started the update — and that progress is reported often enough for the bar to move
 * rather than only at the end.
 *
 * [onProgress] is suspending so the caller can hop to the UI dispatcher per chunk; this function
 * itself touches no dispatcher, which is what keeps it drivable from a plain test.
 */
internal suspend fun copyReportingProgress(
    input: InputStream,
    output: OutputStream,
    contentLength: Long,
    onProgress: suspend (Float) -> Unit,
): Long {
    val buffer = ByteArray(8 * 1024)
    var bytesRead = 0L
    var read: Int
    while (input.read(buffer).also { read = it } != -1) {
        output.write(buffer, 0, read)
        bytesRead += read
        onProgress(downloadProgressFraction(bytesRead, contentLength))
    }
    return bytesRead
}

/**
 * Fraction of the download complete, or -1f (indeterminate) when the server didn't report a
 * content length to measure against.
 */
internal fun downloadProgressFraction(bytesRead: Long, contentLength: Long): Float =
    if (contentLength > 0) (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f) else -1f

@Composable
private fun updateIntervalLabel(interval: UpdateCheckInterval): String = when (interval) {
    UpdateCheckInterval.EVERY_LAUNCH -> stringResource(Res.string.update_interval_every_launch)
    UpdateCheckInterval.WEEKLY -> stringResource(Res.string.update_interval_weekly)
    UpdateCheckInterval.MONTHLY -> stringResource(Res.string.update_interval_monthly)
    UpdateCheckInterval.EVERY_2_MONTHS -> stringResource(Res.string.update_interval_every_2_months)
    UpdateCheckInterval.EVERY_3_MONTHS -> stringResource(Res.string.update_interval_every_3_months)
    UpdateCheckInterval.EVERY_6_MONTHS -> stringResource(Res.string.update_interval_every_6_months)
    UpdateCheckInterval.NEVER -> stringResource(Res.string.update_interval_never)
}

@Composable
private fun UpdateIntervalDropdown(
    selected: UpdateCheckInterval,
    onSelected: (UpdateCheckInterval) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        KeyButton(
            shape = AppShape(6.dp),
            onClick = { expanded = true },
            contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(updateIntervalLabel(selected), style = MaterialTheme.typography.bodySmall)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UpdateCheckInterval.entries.forEach { interval ->
                DropdownMenuItem(
                    text = { Text(updateIntervalLabel(interval), style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onSelected(interval)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    result: UpdateCheckResult?,
    isManualCheck: Boolean,
    participateInPrereleases: Boolean,
    onParticipateInPrereleasesChange: (Boolean) -> Unit,
    updateCheckInterval: UpdateCheckInterval,
    onUpdateCheckIntervalChange: (UpdateCheckInterval) -> Unit,
    onSkipVersion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (result == null) return

    val mainWindowState = LocalMainWindowState.current
    val scope = rememberCoroutineScope()
    val flow = remember(result) { UpdateDownloadFlow((result as? UpdateCheckResult.Available)?.info, scope) }
    val hasUpdate = result is UpdateCheckResult.Available
    val dialogHeight = updateDialogHeight(hasUpdate, isManualCheck)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, UPDATE_DIALOG_WIDTH, dialogHeight),
            width = UPDATE_DIALOG_WIDTH,
            height = dialogHeight
        ),
        title = stringResource(
            if (hasUpdate) Res.string.update_dialog_title else Res.string.update_dialog_up_to_date_title
        ),
        resizable = false
    ) {
        ProvideUiFontScale {
            UpdateAvailableContent(
                result = result,
                isManualCheck = isManualCheck,
                participateInPrereleases = participateInPrereleases,
                onParticipateInPrereleasesChange = onParticipateInPrereleasesChange,
                updateCheckInterval = updateCheckInterval,
                onUpdateCheckIntervalChange = onUpdateCheckIntervalChange,
                downloadState = flow.state,
                onDownload = { flow.download() },
                onCancelDownload = flow::cancel,
                onInstall = flow::install,
                onSkipVersion = onSkipVersion,
                onOpenReleasePage = { UrlOpener.open(it) },
                onDismiss = onDismiss
            )
        }
    }
}

private val UPDATE_DIALOG_WIDTH = 520.dp

/**
 * The window's height. The update-available state's release notes take whatever room is left, so it
 * is the tall one; the up-to-date state is the hero and the settings. A manual check adds the
 * check-interval row.
 */
internal fun updateDialogHeight(hasUpdate: Boolean, isManualCheck: Boolean): Dp = when {
    hasUpdate -> if (isManualCheck) 620.dp else 580.dp
    else -> if (isManualCheck) 330.dp else 290.dp
}

/**
 * Everything the update window shows: the jump from the running version to the new one, its release
 * notes, the beta and check-interval settings, and a footer whose buttons depend on how far the
 * download has got.
 *
 * Held apart from [UpdateAvailableDialog] because that function's other statements all reach the
 * machine — the `DialogWindow` it opens, the HTTP download, launching the installer and quitting,
 * and handing a URL to the desktop browser. None of those can run under the headless suite, and
 * two of them would be actively destructive in one.
 *
 * [downloadState] is passed in rather than owned here so a test can put the dialog into each stage
 * of a download — mid-progress, finished, failed — without one taking place. That state machine is
 * the point of the dialog: it decides whether the operator is offered Download, a progress bar with
 * Cancel, Install Now, or a fallback link to the release page.
 */
@Composable
internal fun UpdateAvailableContent(
    result: UpdateCheckResult,
    isManualCheck: Boolean,
    participateInPrereleases: Boolean,
    onParticipateInPrereleasesChange: (Boolean) -> Unit,
    updateCheckInterval: UpdateCheckInterval,
    onUpdateCheckIntervalChange: (UpdateCheckInterval) -> Unit,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit,
    onCancelDownload: () -> Unit = {},
    onSkipVersion: (String) -> Unit = {},
    /** How the release address is copied, for when the browser opens somewhere unhelpful. */
    copyText: (String) -> Unit = { SystemClipboard.copy(it) }
) {
    val updateInfo = (result as? UpdateCheckResult.Available)?.info

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (updateInfo != null) {
                UpdateHero(updateInfo)
                WhatsNew(updateInfo, onOpenReleasePage)
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                UpToDateHero()
                Spacer(modifier = Modifier.weight(1f))
            }

            BetaRow(participateInPrereleases, onParticipateInPrereleasesChange)
            if (isManualCheck) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.update_dialog_check_interval),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    UpdateIntervalDropdown(
                        selected = updateCheckInterval,
                        onSelected = onUpdateCheckIntervalChange
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            if (updateInfo != null) {
                UpdateFooter(
                    updateInfo,
                    downloadState,
                    UpdateFooterActions(
                        onDownload = onDownload,
                        onCancel = onCancelDownload,
                        onInstall = onInstall,
                        onSkip = {
                            onSkipVersion(updateInfo.latestVersion)
                            onDismiss()
                        },
                        onDismiss = onDismiss,
                        onOpenReleasePage = onOpenReleasePage,
                    ),
                    copyText = copyText,
                )
            } else {
                UpToDateFooter(onOpenReleasePage, onDismiss, copyText)
            }
        }
    }
}
