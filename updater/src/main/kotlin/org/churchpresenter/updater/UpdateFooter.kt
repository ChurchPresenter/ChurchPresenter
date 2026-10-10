package org.churchpresenter.updater

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.CopyLinkIconButton
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.update_dialog_unverified
import org.churchpresenter.strings.generated.resources.update_dialog_dismiss
import org.churchpresenter.strings.generated.resources.update_dialog_download_install
import org.churchpresenter.strings.generated.resources.update_dialog_downloading
import org.churchpresenter.strings.generated.resources.update_dialog_downloading_of
import org.churchpresenter.strings.generated.resources.update_dialog_install_now
import org.churchpresenter.strings.generated.resources.update_dialog_open_page
import org.churchpresenter.strings.generated.resources.update_dialog_ready_to_install
import org.churchpresenter.strings.generated.resources.update_dialog_skip
import org.churchpresenter.strings.generated.resources.update_dialog_view_on_github
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import java.io.File

private val ButtonShape = AppShape(10.dp)
private val PillShape = AppShape(999.dp)

private const val PERCENT = 100

/** What the footer can do while an update is on offer, by how far the download has got. */
internal class UpdateFooterActions(
    val onDownload: () -> Unit,
    val onCancel: () -> Unit,
    val onInstall: (File) -> Unit,
    val onSkip: () -> Unit,
    val onDismiss: () -> Unit,
    val onOpenReleasePage: (String) -> Unit,
)

/**
 * The footer: Skip, Later and Download before anything is fetched; a progress bar and Cancel while it
 * downloads; Ready to install with Later and Install Now once it has; and the release page, with its
 * address to copy, when there is nothing this window can install.
 */
@Composable
internal fun UpdateFooter(
    info: UpdateInfo,
    downloadState: DownloadState,
    actions: UpdateFooterActions,
    copyText: (String) -> Unit,
) {
    FooterRow {
        when {
            downloadState is DownloadState.Downloading -> {
                DownloadBar(downloadState.progress, info.downloadSize, Modifier.weight(1f))
                KeyButton(shape = ButtonShape, onClick = actions.onCancel) {
                    Text(stringResource(Res.string.cancel))
                }
            }
            downloadState is DownloadState.Done -> {
                ReadyToInstall(Modifier.weight(1f))
                KeyButton(shape = ButtonShape, onClick = actions.onDismiss) {
                    Text(stringResource(Res.string.update_dialog_dismiss))
                }
                RaisedButton(
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.semantic.success,
                        contentColor = MaterialTheme.semantic.onSuccess,
                    ),
                    onClick = { actions.onInstall(downloadState.file) },
                ) {
                    Text(stringResource(Res.string.update_dialog_install_now), fontWeight = FontWeight.Bold)
                }
            }
            downloadState is DownloadState.Error || info.downloadUrl == null -> {
                val error = downloadState as? DownloadState.Error
                Text(
                    if (error?.unverified == true) {
                        stringResource(Res.string.update_dialog_unverified)
                    } else {
                        error?.message.orEmpty()
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RaisedButton(
                    shape = ButtonShape,
                    onClick = {
                        actions.onOpenReleasePage(info.releaseUrl)
                        actions.onDismiss()
                    },
                ) {
                    Text(stringResource(Res.string.update_dialog_open_page))
                }
                CopyLinkIconButton(url = info.releaseUrl, onCopy = copyText)
            }
            else -> {
                GhostButton(onClick = actions.onSkip) {
                    Text(
                        stringResource(Res.string.update_dialog_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                KeyButton(shape = ButtonShape, onClick = actions.onDismiss) {
                    Text(stringResource(Res.string.update_dialog_dismiss))
                }
                RaisedButton(shape = ButtonShape, onClick = actions.onDownload) {
                    Text(stringResource(Res.string.update_dialog_download_install), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** "Downloading 210 of 640 MB" and the percentage over the bar, or just "Downloading..." when unsized. */
@Composable
private fun DownloadBar(progress: Float, downloadSize: Long?, modifier: Modifier) {
    val sized = progress >= 0f
    val label = if (sized && downloadSize != null) {
        val total = megabytes(downloadSize)
        stringResource(Res.string.update_dialog_downloading_of, (total * progress).toInt(), total)
    } else {
        stringResource(Res.string.update_dialog_downloading)
    }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sized) {
                Text(
                    "${(progress * PERCENT).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val barModifier = Modifier.fillMaxWidth().height(6.dp).clip(PillShape)
        // A neutral track, so the only colour in the bar is how far it has got.
        val track = MaterialTheme.colorScheme.outlineVariant
        if (sized) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = barModifier,
                trackColor = track,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(modifier = barModifier, trackColor = track, gapSize = 0.dp)
        }
    }
}

@Composable
private fun ReadyToInstall(modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.semantic.success,
        )
        Text(
            stringResource(Res.string.update_dialog_ready_to_install),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.semantic.success,
        )
    }
}

/** With nothing to install: the releases page, its address to copy, and OK. */
@Composable
internal fun UpToDateFooter(onOpenReleasePage: (String) -> Unit, onDismiss: () -> Unit, copyText: (String) -> Unit) {
    FooterRow {
        KeyButton(
            shape = ButtonShape,
            onClick = {
                onOpenReleasePage(UpdateChecker.RELEASES_URL)
                onDismiss()
            },
        ) {
            Text(stringResource(Res.string.update_dialog_view_on_github))
        }
        CopyLinkIconButton(url = UpdateChecker.RELEASES_URL, onCopy = copyText)
        Spacer(Modifier.weight(1f))
        RaisedButton(shape = ButtonShape, onClick = onDismiss) {
            Text(stringResource(Res.string.ok), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FooterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
