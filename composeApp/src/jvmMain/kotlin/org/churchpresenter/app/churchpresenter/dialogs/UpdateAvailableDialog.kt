package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.participate_in_prereleases
import churchpresenter.composeapp.generated.resources.update_dialog_channel_prerelease
import churchpresenter.composeapp.generated.resources.update_dialog_channel_stable
import churchpresenter.composeapp.generated.resources.update_already_latest
import churchpresenter.composeapp.generated.resources.update_dialog_check_interval
import churchpresenter.composeapp.generated.resources.update_dialog_dismiss
import churchpresenter.composeapp.generated.resources.update_dialog_download_install
import churchpresenter.composeapp.generated.resources.update_dialog_downloading
import churchpresenter.composeapp.generated.resources.update_dialog_install_now
import churchpresenter.composeapp.generated.resources.update_dialog_message
import churchpresenter.composeapp.generated.resources.update_dialog_open_page
import churchpresenter.composeapp.generated.resources.update_dialog_release_notes
import churchpresenter.composeapp.generated.resources.update_dialog_title
import churchpresenter.composeapp.generated.resources.update_dialog_up_to_date_title
import churchpresenter.composeapp.generated.resources.update_dialog_view_on_github
import churchpresenter.composeapp.generated.resources.update_interval_every_2_months
import churchpresenter.composeapp.generated.resources.update_interval_every_3_months
import churchpresenter.composeapp.generated.resources.update_interval_every_6_months
import churchpresenter.composeapp.generated.resources.update_interval_every_launch
import churchpresenter.composeapp.generated.resources.update_interval_monthly
import churchpresenter.composeapp.generated.resources.update_interval_never
import churchpresenter.composeapp.generated.resources.update_interval_weekly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckInterval
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckResult
import org.churchpresenter.app.churchpresenter.utils.UpdateChecker
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

private sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState() // -1f = indeterminate
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Launches the downloaded installer using the platform's native mechanism.
 *
 * Deliberately avoids [Desktop.open], which on Windows rejects `.msi` files with
 * "Unsupported URI content". [ProcessBuilder] takes its arguments as a list, so
 * installer paths containing spaces need no shell quoting.
 */
private fun launchInstaller(file: File) {
    val os = System.getProperty("os.name", "").lowercase()
    val path = file.absolutePath
    when {
        // msiexec is the supported way to run an .msi; /i = install.
        os.contains("win") -> ProcessBuilder("msiexec", "/i", path).start()
        // mounts the .dmg in Finder; same as what Desktop.open() does on mac.
        os.contains("mac") -> ProcessBuilder("open", path).start()
        // .deb: hand to the desktop installer Desktop.open() delegates to on Linux.
        else -> ProcessBuilder("xdg-open", path).start()
    }
}

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

/**
 * Circular hero glyph shown above the dialog headline: a solid [circleColor] disc with a
 * centered [icon], wrapped in a soft same-color halo. Colors come from the active theme so
 * the glyph tracks light/dark and every accent theme.
 */
@Composable
private fun HeroIcon(
    icon: ImageVector,
    circleColor: Color,
    iconColor: Color
) {
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(circleColor.copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(circleColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun UpdateIntervalDropdown(
    selected: UpdateCheckInterval,
    onSelected: (UpdateCheckInterval) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            shape = RoundedCornerShape(6.dp),
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
    onDismiss: () -> Unit
) {
    if (result == null) return

    val mainWindowState = LocalMainWindowState.current
    val scope = rememberCoroutineScope()
    var downloadState by remember(result) { mutableStateOf<DownloadState>(DownloadState.Idle) }

    val updateInfo = (result as? UpdateCheckResult.Available)?.info

    val startDownload: () -> Unit = {
        // Count this as an app-updater download (fire-and-forget, own scope —
        // never delays or fails the actual download below).
        updateInfo?.let { UpdateChecker.reportDownloadStarted(it.latestVersion) }
        scope.launch(Dispatchers.IO) {
            try {
                val url = URI(updateInfo!!.downloadUrl!!).toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.connect()

                val contentLength = connection.contentLengthLong
                val suffix = when {
                    updateInfo.downloadUrl.endsWith(".msi", ignoreCase = true) -> ".msi"
                    updateInfo.downloadUrl.endsWith(".dmg", ignoreCase = true) -> ".dmg"
                    updateInfo.downloadUrl.endsWith(".deb", ignoreCase = true) -> ".deb"
                    else -> ".bin"
                }
                // NB: do not deleteOnExit() — the installer is launched as the
                // app exits via exitProcess(0), and the shutdown hook would
                // delete the file out from under the installer.
                val tempFile = File.createTempFile("ChurchPresenter-update", suffix)

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val progress = if (contentLength > 0)
                                (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            else -1f
                            withContext(Dispatchers.Main) {
                                downloadState = DownloadState.Downloading(progress)
                            }
                        }
                    }
                }
                connection.disconnect()
                withContext(Dispatchers.Main) {
                    downloadState = DownloadState.Done(tempFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadState = DownloadState.Error(e.message ?: "Download failed")
                }
            }
        }
    }

    val dialogTitle = if (updateInfo != null)
        stringResource(Res.string.update_dialog_title)
    else
        stringResource(Res.string.update_dialog_up_to_date_title)

    // Extra height for the update-available state, whose hero sits above a full release-notes
    // panel; the up-to-date state's flexible spacer absorbs the hero at the original heights.
    val dialogHeight = when {
        updateInfo != null -> if (isManualCheck) 548.dp else 500.dp
        else -> if (isManualCheck) 468.dp else 420.dp
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 440.dp, dialogHeight),
            width = 440.dp,
            height = dialogHeight
        ),
        title = dialogTitle,
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (updateInfo != null) {
                    HeroIcon(
                        icon = Icons.Default.Download,
                        circleColor = MaterialTheme.colorScheme.primary,
                        iconColor = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.update_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.update_dialog_message, updateInfo.latestVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = if (updateInfo.isPrerelease)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (updateInfo.isPrerelease)
                                stringResource(Res.string.update_dialog_channel_prerelease)
                            else
                                stringResource(Res.string.update_dialog_channel_stable),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (updateInfo.isPrerelease)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (updateInfo.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(Res.string.update_dialog_release_notes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Progress area
                    when (val state = downloadState) {
                        is DownloadState.Downloading -> {
                            val progressText = if (state.progress >= 0f)
                                "${(state.progress * 100).toInt()}%"
                            else
                                stringResource(Res.string.update_dialog_downloading)
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (state.progress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is DownloadState.Error -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        else -> {}
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    HeroIcon(
                        icon = Icons.Default.Check,
                        circleColor = MaterialTheme.colorScheme.inverseSurface,
                        iconColor = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.update_dialog_up_to_date_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.update_already_latest),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.participate_in_prereleases),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = participateInPrereleases,
                        onCheckedChange = onParticipateInPrereleasesChange
                    )
                }
                if (isManualCheck) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.update_dialog_check_interval),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        UpdateIntervalDropdown(
                            selected = updateCheckInterval,
                            onSelected = onUpdateCheckIntervalChange
                        )
                    }
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (updateInfo != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                            Text(stringResource(Res.string.update_dialog_dismiss))
                        }
                        when {
                            downloadState is DownloadState.Done -> {
                                Button(
                                    shape = RoundedCornerShape(6.dp),
                                    onClick = {
                                    val file = (downloadState as DownloadState.Done).file
                                    try {
                                        launchInstaller(file)
                                        exitProcess(0)
                                    } catch (e: Exception) {
                                        downloadState = DownloadState.Error(
                                            e.message ?: "Failed to launch installer"
                                        )
                                    }
                                }) {
                                    Text(stringResource(Res.string.update_dialog_install_now))
                                }
                            }
                            downloadState is DownloadState.Downloading -> {
                                Button(shape = RoundedCornerShape(6.dp), onClick = {}, enabled = false) {
                                    Text(stringResource(Res.string.update_dialog_downloading))
                                }
                            }
                            downloadState is DownloadState.Error || updateInfo.downloadUrl == null -> {
                                Button(
                                    shape = RoundedCornerShape(6.dp),
                                    onClick = {
                                    Desktop.getDesktop().browse(URI(updateInfo.releaseUrl))
                                    onDismiss()
                                }) {
                                    Text(stringResource(Res.string.update_dialog_open_page))
                                }
                            }
                            else -> {
                                Button(shape = RoundedCornerShape(6.dp), onClick = startDownload) {
                                    Text(stringResource(Res.string.update_dialog_download_install))
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            onClick = {
                                Desktop.getDesktop().browse(URI(UpdateChecker.RELEASES_URL))
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(Res.string.update_dialog_view_on_github))
                        }
                        Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                            Text(stringResource(Res.string.ok))
                        }
                    }
                }
            }
        }
    }
}
