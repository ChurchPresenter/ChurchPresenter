package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.about_copyright
import churchpresenter.composeapp.generated.resources.about_title
import churchpresenter.composeapp.generated.resources.app_name
import churchpresenter.composeapp.generated.resources.action_ok
import churchpresenter.composeapp.generated.resources.converter_window_title
import churchpresenter.composeapp.generated.resources.diagnostic_info_save_failed
import churchpresenter.composeapp.generated.resources.diagnostic_info_saved
import churchpresenter.composeapp.generated.resources.lottie_gen_window_title
import churchpresenter.composeapp.generated.resources.open_crash_logs
import churchpresenter.composeapp.generated.resources.report_bug
import churchpresenter.composeapp.generated.resources.save_diagnostic_info
import churchpresenter.composeapp.generated.resources.style_editor_window_title
import churchpresenter.composeapp.generated.resources.submit_feature_request
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.DeviceInfoReport
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.ic_app_icon
import ui.App as ConverterApp
import lottiegen.App as LottieGenApp
import lottiegen.editor.StyleEditorApp
import java.awt.Desktop
import java.awt.Window as AwtWindow
import java.io.File
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText

@Composable
fun AboutDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    theme: ThemeMode = ThemeMode.SYSTEM
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 400.dp, 410.dp),
            width = 400.dp,
            height = 410.dp
        ),
        title = stringResource(Res.string.about_title),
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = BuildConfig.VERSION_DISPLAY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.about_copyright, "2026"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Desktop.getDesktop().browse(java.net.URI("https://github.com/ChurchPresenter/ChurchPresenter/issues/new?template=bug_report.md"))
                        }
                    ) {
                        Text(stringResource(Res.string.report_bug), maxLines = 2, textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Desktop.getDesktop().browse(java.net.URI("https://github.com/ChurchPresenter/ChurchPresenter/issues/new?template=feature_request.md"))
                        }
                    ) {
                        Text(stringResource(Res.string.submit_feature_request), maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    shape = RoundedCornerShape(6.dp),
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
                val saveTitle = stringResource(Res.string.save_diagnostic_info)
                val savedMsg = stringResource(Res.string.diagnostic_info_saved)
                val saveFailedMsg = stringResource(Res.string.diagnostic_info_save_failed)
                val saveCoroutineScope = rememberCoroutineScope()
                OutlinedButton(
                    shape = RoundedCornerShape(6.dp),
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
                                    path.writeText(DeviceInfoReport.generate(appSettings))
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
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            }
        }
    }
}

@Composable
fun ConverterWindow(theme: ThemeMode, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.converter_window_title),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 1100.dp, height = 800.dp)
    ) {
        AppThemeWrapper(theme = theme) {
            ConverterApp()
        }
    }
}

@Composable
fun LottieGenWindow(theme: ThemeMode, outputDir: File?, onClose: () -> Unit, onFileSaved: (() -> Unit)? = null, canvasWidth: Int? = null, canvasHeight: Int? = null) {
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.lottie_gen_window_title),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        AppThemeWrapper(theme = theme) {
            LottieGenApp(outputDir = outputDir, onFileSaved = onFileSaved, canvasWidth = canvasWidth, canvasHeight = canvasHeight)
        }
    }
}

@Composable
fun StyleEditorWindow(theme: ThemeMode, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.style_editor_window_title),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 1500.dp, height = 950.dp)
    ) {
        AppThemeWrapper(theme = theme) {
            StyleEditorApp(standalone = false)
        }
    }
}
