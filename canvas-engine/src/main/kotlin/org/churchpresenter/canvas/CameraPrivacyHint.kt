package org.churchpresenter.canvas

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.utils.UrlOpener
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_camera_open_privacy_settings
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.stringResource

/** The macOS Camera privacy pane, opened from a panel because a panel is where it can be acted on. */
internal const val MAC_CAMERA_PRIVACY_URI =
    "x-apple.systempreferences:com.apple.preference.security?Privacy_Camera"

/** The Windows Camera privacy page, opened for the same reason as [MAC_CAMERA_PRIVACY_URI]. */
internal const val WINDOWS_CAMERA_PRIVACY_URI = "ms-settings:privacy-webcam"

/**
 * Where [osName] keeps its camera permission, or null on a platform with no such page to open.
 *
 * Windows has two separate switches there — camera access at all, and desktop apps in particular —
 * and blocks with either off, which is why the button leads to the page rather than the text trying
 * to describe the sequence.
 */
internal fun cameraPrivacyUri(osName: String): String? {
    val name = osName.lowercase()
    return when {
        name.contains("mac") || name.contains("darwin") -> MAC_CAMERA_PRIVACY_URI
        name.contains("win") -> WINDOWS_CAMERA_PRIVACY_URI
        else -> null
    }
}

/**
 * The way out of a privacy refusal, shown beside a camera picker.
 *
 * It lives in a panel rather than on the canvas because the canvas composable is also what the
 * presenter output draws — a button there would be painted onto the screen the congregation is
 * looking at. The canvas says *what* is wrong; this is where it is acted on.
 *
 * No accompanying warning text: a camera that is working needs no explanation, and a panel that
 * announces the OS is blocking something whenever it runs on that OS is a panel operators learn to
 * read past. The button is a plain affordance, and the diagnosis is carried beside it.
 *
 * Shared with the background camera picker rather than copied to it: a background is opened by the
 * presenter window before any picker has been looked at, so it is the *more* likely of the two to
 * be refused, and it had no way out at all.
 *
 * [openUri] is a parameter so a test can press the button without launching a browser.
 */
@Composable
fun CameraPrivacyHint(
    osName: String = System.getProperty("os.name", ""),
    openUri: (String) -> Unit = { UrlOpener.open(it) },
) {
    val uri = cameraPrivacyUri(osName) ?: return
    RaisedButton(onClick = { openUri(uri) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.canvas_camera_open_privacy_settings), fontSize = 12.sp)
    }
}
