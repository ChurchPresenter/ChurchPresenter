package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.app.churchpresenter.utils.UpdateInfo
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.update_dialog_channel_prerelease
import org.churchpresenter.strings.generated.resources.update_dialog_channel_stable
import org.churchpresenter.strings.generated.resources.update_already_latest
import org.churchpresenter.strings.generated.resources.update_dialog_dismiss
import org.churchpresenter.strings.generated.resources.update_dialog_download_install
import org.churchpresenter.strings.generated.resources.update_dialog_downloading
import org.churchpresenter.strings.generated.resources.update_dialog_install_now
import org.churchpresenter.strings.generated.resources.update_dialog_open_page
import org.churchpresenter.strings.generated.resources.update_dialog_release_notes
import org.churchpresenter.strings.generated.resources.update_dialog_up_to_date_title
import org.churchpresenter.strings.generated.resources.update_dialog_view_on_github
import org.churchpresenter.app.churchpresenter.utils.UpdateChecker
import org.jetbrains.compose.resources.stringResource
import java.io.File
import org.churchpresenter.sharedui.composables.CopyLinkIconButton


/** Whether the offered version is a stable release or a prerelease. */
@Composable
internal fun ChannelBadge(isPrerelease: Boolean) {
    Surface(
        color = if (isPrerelease)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.primaryContainer,
        shape = AppShape(4.dp)
    ) {
        Text(
            text = if (isPrerelease)
                stringResource(Res.string.update_dialog_channel_prerelease)
            else
                stringResource(Res.string.update_dialog_channel_stable),
            style = MaterialTheme.typography.labelSmall,
            color = if (isPrerelease)
                MaterialTheme.colorScheme.onTertiaryContainer
            else
                MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** The release notes in their own scrolling box, when the release has any. */
@Composable
internal fun ColumnScope.ReleaseNotes(releaseNotes: String) {
    if (releaseNotes.isNotBlank()) {
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
                text = releaseNotes,
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
}

/** How far the download has got, or why it failed. */
@Composable
internal fun DownloadProgress(downloadState: DownloadState) {
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
}

/** The tick and the words saying this is already the newest version. */
@Composable
internal fun UpToDateHeader() {
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
}

/** Dismiss, then download, install or open the release page, by how far the download has got. */
@Composable
internal fun UpdateActions(
    updateInfo: UpdateInfo,
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDownload: () -> Unit,
    copyText: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyButton(shape = AppShape(6.dp), onClick = onDismiss) {
            Text(stringResource(Res.string.update_dialog_dismiss))
        }
        when {
            downloadState is DownloadState.Done -> {
                RaisedButton(
                    shape = AppShape(6.dp),
                    onClick = { onInstall(downloadState.file) }
                ) {
                    Text(stringResource(Res.string.update_dialog_install_now))
                }
            }
            downloadState is DownloadState.Downloading -> {
                RaisedButton(shape = AppShape(6.dp), onClick = {}, enabled = false) {
                    Text(stringResource(Res.string.update_dialog_downloading))
                }
            }
            downloadState is DownloadState.Error || updateInfo.downloadUrl == null -> {
                RaisedButton(
                    shape = AppShape(6.dp),
                    onClick = {
                        onOpenReleasePage(updateInfo.releaseUrl)
                        onDismiss()
                    }
                ) {
                    Text(stringResource(Res.string.update_dialog_open_page))
                }
                CopyLinkIconButton(url = updateInfo.releaseUrl, onCopy = copyText)
            }
            else -> {
                RaisedButton(shape = AppShape(6.dp), onClick = onDownload) {
                    Text(stringResource(Res.string.update_dialog_download_install))
                }
            }
        }
    }
}

/** With nothing to install: the releases page, its address to copy, and OK. */
@Composable
internal fun UpToDateActions(onOpenReleasePage: (String) -> Unit, onDismiss: () -> Unit, copyText: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyButton(
            modifier = Modifier.weight(1f),
            shape = AppShape(6.dp),
            onClick = {
                onOpenReleasePage(UpdateChecker.RELEASES_URL)
                onDismiss()
            }
        ) {
            Text(stringResource(Res.string.update_dialog_view_on_github))
        }
        CopyLinkIconButton(url = UpdateChecker.RELEASES_URL, onCopy = copyText)
        RaisedButton(shape = AppShape(6.dp), onClick = onDismiss) {
            Text(stringResource(Res.string.ok))
        }
    }

}
