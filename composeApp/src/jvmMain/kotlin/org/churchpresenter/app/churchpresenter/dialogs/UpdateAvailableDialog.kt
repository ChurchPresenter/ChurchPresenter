package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.shape.RoundedCornerShape
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.update_dialog_dismiss
import churchpresenter.composeapp.generated.resources.update_dialog_download_install
import churchpresenter.composeapp.generated.resources.update_dialog_downloading
import churchpresenter.composeapp.generated.resources.update_dialog_install_now
import churchpresenter.composeapp.generated.resources.update_dialog_message
import churchpresenter.composeapp.generated.resources.update_dialog_open_page
import churchpresenter.composeapp.generated.resources.update_dialog_release_notes
import churchpresenter.composeapp.generated.resources.update_dialog_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.utils.UpdateInfo
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
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo?,
    onDismiss: () -> Unit
) {
    if (updateInfo == null) return

    val mainWindowState = LocalMainWindowState.current
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    val startDownload: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            try {
                val url = URI(updateInfo.downloadUrl!!).toURL()
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

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 440.dp, 380.dp),
            width = 440.dp,
            height = 380.dp
        ),
        title = stringResource(Res.string.update_dialog_title),
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
            }
        }
    }
}
