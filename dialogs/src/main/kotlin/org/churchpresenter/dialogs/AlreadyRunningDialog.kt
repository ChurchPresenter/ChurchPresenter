package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.already_running_message
import org.churchpresenter.strings.generated.resources.already_running_ok
import org.churchpresenter.strings.generated.resources.already_running_title
import org.churchpresenter.theme.LocalThemeCustomization
import org.churchpresenter.theme.ThemeCustomization
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.themeFromSettings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

private val DIALOG_SIZE = DpSize(440.dp, 190.dp)

/**
 * Tells someone who opened a second copy that ChurchPresenter is already running, drawn in the
 * app's own theme -- the one saved in [settings] -- rather than as a bare system message box.
 * Returns when it is closed; the caller then exits.
 */
fun showAlreadyRunningDialog(
    settings: AppSettings,
    themeCustomization: ThemeCustomization,
) = application(exitProcessOnExit = false) {
    DialogWindow(
        onCloseRequest = ::exitApplication,
        state = rememberDialogState(position = WindowPosition(Alignment.Center), size = DIALOG_SIZE),
        title = stringResource(Res.string.already_running_title),
        icon = painterResource(IconRes.drawable.ic_app_icon),
        resizable = false,
    ) {
        CompositionLocalProvider(LocalThemeCustomization provides themeCustomization) {
            AppWindowRoot(theme = themeFromSettings(settings.theme)) {
                AlreadyRunningContent(onDismiss = ::exitApplication)
            }
        }
    }
}

/** The dialog's body: the app's icon, what happened, and the one button. */
@Composable
internal fun AlreadyRunningContent(onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(IconRes.drawable.ic_app_icon),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(Res.string.already_running_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(Res.string.already_running_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RaisedButton(onClick = onDismiss, modifier = Modifier.height(36.dp)) {
                    Text(stringResource(Res.string.already_running_ok))
                }
            }
        }
    }
}
