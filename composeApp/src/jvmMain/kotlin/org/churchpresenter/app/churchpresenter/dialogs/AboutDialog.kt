package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.about_title
import churchpresenter.composeapp.generated.resources.app_name
import churchpresenter.composeapp.generated.resources.action_ok
import churchpresenter.composeapp.generated.resources.open_crash_logs
import churchpresenter.composeapp.generated.resources.open_converter
import churchpresenter.composeapp.generated.resources.report_bug
import churchpresenter.composeapp.generated.resources.submit_feature_request
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.ic_app_icon
import ui.App as ConverterApp
import java.awt.Desktop
import java.io.File

@Composable
fun AboutDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    var showConverter by remember { mutableStateOf(false) }

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 360.dp, 340.dp),
            width = 360.dp,
            height = 340.dp
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
                    text = "© 2025 Church Presenter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        Desktop.getDesktop().browse(java.net.URI("https://github.com/ChurchPresenter/ChurchPresenter/issues/new?template=bug_report.md"))
                    }) {
                        Text(stringResource(Res.string.report_bug))
                    }
                    OutlinedButton(onClick = {
                        Desktop.getDesktop().browse(java.net.URI("https://github.com/ChurchPresenter/ChurchPresenter/issues/new?template=feature_request.md"))
                    }) {
                        Text(stringResource(Res.string.submit_feature_request))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { showConverter = true }) {
                        Text(stringResource(Res.string.open_converter))
                    }
                    OutlinedButton(onClick = {
                        val crashDir = File(System.getProperty("user.home"), ".churchpresenter/crash-reports")
                        crashDir.mkdirs()
                        Desktop.getDesktop().open(crashDir)
                    }) {
                        Text(stringResource(Res.string.open_crash_logs))
                    }
                    Button(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_ok))
                    }
                }
            }
        }
    }

    if (showConverter) {
        ConverterWindow(onClose = { showConverter = false })
    }
}

@Composable
private fun ConverterWindow(onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = "ChurchPresenter Converter",
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 1100.dp, height = 800.dp)
    ) {
        ConverterApp()
    }
}
