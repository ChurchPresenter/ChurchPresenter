package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.app.churchpresenter.utils.appTelemetryIdentity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.theme.AppShape
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.about_copyright
import org.churchpresenter.strings.generated.resources.ndi_trademark
import org.churchpresenter.strings.generated.resources.about_title
import org.churchpresenter.strings.generated.resources.app_name
import org.churchpresenter.strings.generated.resources.action_ok
import org.churchpresenter.strings.generated.resources.diagnostic_info_save_failed
import org.churchpresenter.strings.generated.resources.diagnostic_info_saved
import org.churchpresenter.strings.generated.resources.open_crash_logs
import org.churchpresenter.strings.generated.resources.report_bug
import org.churchpresenter.strings.generated.resources.save_diagnostic_info
import org.churchpresenter.strings.generated.resources.submit_feature_request
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.telemetry.DeviceInfoReport
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.ic_app_icon
import java.awt.Desktop
import java.awt.Window as AwtWindow
import java.io.File
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText
import org.churchpresenter.sharedui.composables.CopyLinkIconButton
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.sharedui.utils.UrlOpener

private const val GRADIENT_DARKEN = 0.45f

@Composable
fun AboutDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    theme: ThemeMode = ThemeMode.SYSTEM,
    /** The window it opens in -- see [DialogFrame]. */
    frame: DialogFrame = appDialogFrame,
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    frame(
        DialogFrameSpec(
            onClose = onDismiss,
            state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, ABOUT_DIALOG_WIDTH, ABOUT_DIALOG_HEIGHT),
            width = ABOUT_DIALOG_WIDTH,
            height = ABOUT_DIALOG_HEIGHT
        ),
            title = stringResource(Res.string.about_title),
            resizable = false,
        ),
    ) {
        AboutDialogContent(onDismiss = onDismiss, appSettings = appSettings, theme = theme)
    }
}

/** The GitHub issue template the "Report a Bug" button opens, and its copy button copies. */
internal const val BUG_REPORT_URL =
    "https://github.com/ChurchPresenter/ChurchPresenter/issues/new?template=bug_report.md"

/** The GitHub issue template behind "Feature Request". */
internal const val FEATURE_REQUEST_URL =
    "https://github.com/ChurchPresenter/ChurchPresenter/issues/new?template=feature_request.md"

/** One GitHub issue template: a full-width button that opens it, and a button that copies it. */
@Composable
private fun IssueLinkRow(
    label: String,
    url: String,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyButton(
            shape = AppShape(6.dp),
            modifier = Modifier.weight(1f),
            onClick = { onOpen(url) }
        ) {
            Text(label, maxLines = 2, textAlign = TextAlign.Center)
        }
        CopyLinkIconButton(url = url, onCopy = onCopy)
    }
}

@Composable
internal fun AboutDialogContent(
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    theme: ThemeMode,
    /**
     * The version line, as a parameter only so the screenshot of this dialog can pin it.
     *
     * [BuildConfig.VERSION_DISPLAY] carries the build's git hash, so it changes with every commit —
     * and a committed image of this dialog would therefore be stale the moment it was recorded, and
     * would fail `verifyRoborazziJvm` for ever after. Nothing but the test passes anything here.
     */
    versionDisplay: String = BuildConfig.VERSION_DISPLAY,
    /** How an issue template is opened. A parameter so a test does not launch a real browser. */
    openUrl: (String) -> Unit = { UrlOpener.open(it) },
    /** How an issue-template address is copied, for when the browser opens on the wrong screen. */
    copyText: (String) -> Unit = { SystemClipboard.copy(it) },
) {
    AppWindowRoot(theme = theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App icon on a gradient tile
                AppIconTile()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = versionDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.about_copyright, "2026"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Required by NDI's licence terms wherever the app offers NDI, alongside the same
                // line on the Projection settings card. Not optional, and not conditional on a
                // runtime being installed: the app offers the feature either way.
                Text(
                    text = stringResource(Res.string.ndi_trademark),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                // One link per row rather than two side by side. Side by side, the copy buttons
                // took enough width that "Feature Request" wrapped to two lines and its button
                // grew taller than the one beside it; a row each matches the full-width buttons
                // below and leaves every label on one line.
                IssueLinkRow(
                    label = stringResource(Res.string.report_bug),
                    url = BUG_REPORT_URL,
                    onOpen = openUrl,
                    onCopy = copyText,
                )
                Spacer(modifier = Modifier.height(8.dp))
                IssueLinkRow(
                    label = stringResource(Res.string.submit_feature_request),
                    url = FEATURE_REQUEST_URL,
                    onOpen = openUrl,
                    onCopy = copyText,
                )
                Spacer(modifier = Modifier.height(8.dp))
                KeyButton(
                    shape = AppShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val crashDir = File(System.getProperty("user.home"), ".churchpresenter/crash-reports")
                        crashDir.mkdirs()
                        Desktop.getDesktop().open(crashDir)
                    }
                ) {
                    Text(stringResource(Res.string.open_crash_logs), maxLines = 1, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(8.dp))
                SaveDiagnosticInfoButton(appSettings)
                Spacer(modifier = Modifier.height(8.dp))
                RaisedButton(
                    shape = AppShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            }
        }
    }
}

/** The app icon on its gradient tile, at the top of the dialog. */
@Composable
private fun AppIconTile() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(AppShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(MaterialTheme.colorScheme.primary, Color.Black, GRADIENT_DARKEN),
                        MaterialTheme.colorScheme.primary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(IconRes.drawable.ic_app_icon),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
    }
}

/** Writes the diagnostic report to a file the operator picks, and says whether it worked. */
@Composable
private fun SaveDiagnosticInfoButton(appSettings: AppSettings) {
    val saveTitle = stringResource(Res.string.save_diagnostic_info)
    val savedMsg = stringResource(Res.string.diagnostic_info_saved)
    val saveFailedMsg = stringResource(Res.string.diagnostic_info_save_failed)
    val saveCoroutineScope = rememberCoroutineScope()
    KeyButton(
        shape = AppShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            saveCoroutineScope.launch {
                var path = FileChooser.platformInstance.save(
                    location = null,
                    suggestedName = "churchpresenter-diagnostic-info.txt",
                    filters = listOf(FileNameExtensionFilter("Text (*.txt)", "txt")),
                    title = saveTitle
                )
                if (path != null) {
                    try {
                        if (path.extension != "txt") {
                            path = path.resolveSibling("${path.nameWithoutExtension}.txt")
                        }
                        path.writeText(DeviceInfoReport.generate(appSettings, appTelemetryIdentity))
                        JOptionPane.showMessageDialog(
                            AwtWindow.getWindows().firstOrNull { it.isActive },
                            savedMsg,
                            saveTitle,
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    } catch (_: Exception) {
                        JOptionPane.showMessageDialog(
                            AwtWindow.getWindows().firstOrNull { it.isActive },
                            saveFailedMsg,
                            saveTitle,
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
    ) {
        Text(saveTitle, maxLines = 1, textAlign = TextAlign.Center)
    }
}
