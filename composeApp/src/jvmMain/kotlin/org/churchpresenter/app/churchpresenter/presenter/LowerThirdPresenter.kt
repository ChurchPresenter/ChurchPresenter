package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.keyColorFilter
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.LottieFonts

/**
 * Displays a Lottie animation in the presenter window.
 *
 * When a pre-decoded [frame] is available (served by PresenterManager's LottieFrameStream
 * from the shared render cache), it is drawn directly — one bitmap decode per frame for all
 * windows together, off the UI thread. This eliminates per-frame rendering variance across
 * GPU vendors and drivers. Until then the live Compottie painter renders as a fallback.
 *
 * Animation progress is driven centrally by main.kt's playback clock so all presenter
 * displays stay in perfect sync.
 */
@Composable
fun LowerThirdPresenter(
    composition: LottieComposition?,
    progress: () -> Float,
    appSettings: AppSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    frame: LottieFrame? = null
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    if (frame == null && composition == null) return

    val s = appSettings.streamingSettings
    val contentModifier = Modifier
        .padding(
            start = s.windowLeft.dp,
            end = s.windowRight.dp,
            top = s.windowTop.dp,
            bottom = s.windowBottom.dp
        )
        .fillMaxSize()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (composition != null) {
            // Keep the live Compottie painter mounted for the whole play once composition has
            // loaded (its identity is stable for an entire play — it only changes between
            // distinct plays), and pick which bitmap to actually draw via a plain value check
            // rather than a key()-forced subtree swap. main.kt's playback loop switches to raw
            // frames mid-play as soon as they're ready; tearing down and remounting the live
            // painter at that exact moment caused a visible hitch.
            val painter = rememberLottiePainter(
                composition = composition,
                progress = progress,
                fontManager = LottieFonts
            )
            if (frame != null) {
                Image(
                    bitmap = frame.imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isKey) keyColorFilter else null,
                    modifier = contentModifier
                )
            } else {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isKey) keyColorFilter else null,
                    modifier = contentModifier
                )
            }
        } else if (frame != null) {
            // Rare: pre-rendered frames became ready before Compottie's own composition parse.
            Image(
                bitmap = frame.imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = if (isKey) keyColorFilter else null,
                modifier = contentModifier
            )
        }
    }
}
