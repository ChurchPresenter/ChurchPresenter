package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.calendar_enroll_qr_body
import churchpresenter.composeapp.generated.resources.calendar_enroll_qr_title
import churchpresenter.composeapp.generated.resources.close
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.dialogs.tabs.connectionQrBitmap
import org.churchpresenter.app.churchpresenter.server.CalendarEnrollment
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

/** The QR a just-approved phone scans to finish enrolling. Drawn only; there is no text form. */
@Composable
fun CalendarEnrollQrDialog(enrollment: CalendarEnrollment, onDismiss: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE
    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, DIALOG_WIDTH, DIALOG_HEIGHT),
            width = DIALOG_WIDTH,
            height = DIALOG_HEIGHT,
        ),
        title = stringResource(Res.string.calendar_enroll_qr_title),
        resizable = false,
    ) {
        AppWindowRoot(theme = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
            CalendarEnrollQrContent(enrollment, onDismiss)
        }
    }
}

@Composable
internal fun CalendarEnrollQrContent(enrollment: CalendarEnrollment, onDismiss: () -> Unit) {
    val bitmap = remember(enrollment) { connectionQrBitmap(enrollment.qrContent, QR_PX) }
    // The QR carries the phone's token and the calendar key, and this machine is often on a projector
    // or a stream. It goes away by itself; the phone can ask again.
    LaunchedEffect(enrollment) {
        delay(QR_LIFETIME_MS)
        onDismiss()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(QR_DP),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = stringResource(Res.string.calendar_enroll_qr_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
            )
            Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                Text(stringResource(Res.string.close), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * `Code 482 913 — allow only if…` — the phone's code as the approval prompt shows it, split for
 * reading aloud. [format] is `remote_api_calendar_enroll_code`, resolved by the caller where a
 * composable can, because the prompt is built inside a flow collector.
 */
fun enrollCodeText(code: String, format: String): String = format.format(code.chunked(CODE_GROUP).joinToString(" "))

private const val DARK_LUMINANCE = 0.5f
private const val CODE_GROUP = 3
private const val QR_PX = 512
private const val QR_LIFETIME_MS = 120_000L
private val QR_DP = 300.dp
private val DIALOG_WIDTH = 400.dp
private val DIALOG_HEIGHT = 460.dp
