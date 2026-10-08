package org.churchpresenter.appsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Warning
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.strings.generated.resources.setup_media_why_body
import org.churchpresenter.strings.generated.resources.setup_media_why_title
import org.churchpresenter.strings.generated.resources.setup_step5_download
import org.churchpresenter.strings.generated.resources.setup_step5_download_intel
import org.churchpresenter.strings.generated.resources.setup_step5_download_silicon
import org.churchpresenter.strings.generated.resources.setup_step5_linux_tip
import org.churchpresenter.strings.generated.resources.setup_step5_recheck
import org.churchpresenter.strings.generated.resources.setup_step5_subtitle
import org.churchpresenter.strings.generated.resources.setup_step5_title
import org.churchpresenter.strings.generated.resources.setup_step5_vlc_load_failed
import org.churchpresenter.strings.generated.resources.setup_step5_vlc_load_failed_detail
import org.churchpresenter.strings.generated.resources.setup_step5_vlc_missing
import org.churchpresenter.strings.generated.resources.setup_step5_vlc_ok
import org.churchpresenter.strings.generated.resources.setup_step5_vlc_wrong_arch
import org.churchpresenter.strings.generated.resources.setup_step5_vlc_wrong_arch_detail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.composables.CopyLinkIconButton
import org.churchpresenter.media.composables.isVlcArchMismatch
import org.churchpresenter.media.composables.isVlcAvailable
import org.churchpresenter.media.composables.isVlcLoadFailed
import org.churchpresenter.media.composables.recheckVlcAvailability
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.sharedui.utils.UrlOpener
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/** The real probe: re-runs [recheckVlcAvailability] and reads the two detail flags it leaves behind. */
private fun vlcCheckResultFromRecheck(): VlcCheckResult =
    VlcCheckResult(recheckVlcAvailability(), isVlcArchMismatch, isVlcLoadFailed)

@Composable
internal fun VlcStep(
    initial: VlcCheckResult = VlcCheckResult(isVlcAvailable, isVlcArchMismatch, isVlcLoadFailed),
    osName: String = System.getProperty("os.name", "").lowercase(),
    arch: String = System.getProperty("os.arch", "").lowercase(),
    onRecheck: suspend () -> VlcCheckResult = { vlcCheckResultFromRecheck() },
    onOpenDownloadPage: (String) -> Unit = { UrlOpener.open(it) },
    /**
     * How the download address is copied.
     *
     * The wizard is where a machine whose browser cannot be reached is most likely to be met — and
     * where it opens is the operating system's choice, not the app's, so on a two-screen setup the
     * page can land on the projection output. A parameter for the same reason
     * [onOpenDownloadPage] is one: a test observes the copy rather than taking the real clipboard.
     */
    copyText: (String) -> Unit = { SystemClipboard.copy(it) }
) {
    val isMac = remember { "mac" in osName || "darwin" in osName }
    val isWin = remember { "win" in osName }
    val isArm = remember { "aarch64" in arch || "arm" in arch }
    val isLinux = remember { !isMac && !isWin }

    var vlcOk by remember { mutableStateOf(initial.available) }
    var archMismatch by remember { mutableStateOf(initial.archMismatch) }
    var loadFailed by remember { mutableStateOf(initial.loadFailed) }
    var rechecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val downloadUrl = remember {
        when {
            isWin -> "https://www.videolan.org/vlc/download-windows.html"
            isMac -> "https://www.videolan.org/vlc/download-macosx.html"
            else -> "https://www.videolan.org/vlc/download-linux.html"
        }
    }

    WizardPanelHeader(
        icon = Icons.Filled.OndemandVideo,
        title = stringResource(Res.string.setup_step5_title),
        subtitle = stringResource(Res.string.setup_step5_subtitle),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape(10.dp))
            .background(
                if (vlcOk) MaterialTheme.semantic.successContainer
                else MaterialTheme.colorScheme.errorContainer
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VlcStatusLine(vlcOk, archMismatch, loadFailed)
        if (!vlcOk) {
            if (archMismatch) {
                Text(
                    text = stringResource(Res.string.setup_step5_vlc_wrong_arch_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (loadFailed) {
                Text(
                    text = stringResource(Res.string.setup_step5_vlc_load_failed_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RaisedButton(
                    shape = AppShape(8.dp),
                    onClick = { onOpenDownloadPage(downloadUrl) }
                ) {
                    Text(
                        stringResource(
                            when {
                                isMac && isArm -> Res.string.setup_step5_download_silicon
                                isMac -> Res.string.setup_step5_download_intel
                                else -> Res.string.setup_step5_download
                            }
                        )
                    )
                }
                KeyButton(
                    shape = AppShape(8.dp),
                    onClick = {
                        scope.launch {
                            rechecking = true
                            val result = withContext(Dispatchers.IO) { onRecheck() }
                            vlcOk = result.available
                            archMismatch = result.archMismatch
                            loadFailed = result.loadFailed
                            rechecking = false
                        }
                    },
                    enabled = !rechecking
                ) {
                    Text(stringResource(Res.string.setup_step5_recheck))
                }
                CopyLinkIconButton(url = downloadUrl, onCopy = copyText)
            }
        }
    }
    InfoCard(
        title = stringResource(Res.string.setup_media_why_title),
        body = stringResource(Res.string.setup_media_why_body),
    )
    if (isLinux) {
        TipBox(text = stringResource(Res.string.setup_step5_linux_tip))
    }
}

/** Whether VLC was found, and if not, which of the three ways it was not. */
@Composable
private fun VlcStatusLine(vlcOk: Boolean, archMismatch: Boolean, loadFailed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = if (vlcOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (vlcOk) MaterialTheme.semantic.onSuccessContainer else MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(
                when {
                    vlcOk -> Res.string.setup_step5_vlc_ok
                    archMismatch -> Res.string.setup_step5_vlc_wrong_arch
                    loadFailed -> Res.string.setup_step5_vlc_load_failed
                    else -> Res.string.setup_step5_vlc_missing
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (vlcOk) MaterialTheme.semantic.onSuccessContainer else MaterialTheme.colorScheme.error,
        )
    }
}
