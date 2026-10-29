@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO

// A phone drawn from the mobile app's own screenshots, and the sheets it shows floated over it, for the
// tours of the mobile app (Standalone, Calendar mode).

/** The mobile app's picture sets: `CP_MOBILE_SHOTS`'s parent, else the checkout beside this one. */
internal val MOBILE_ROOT = File(
    System.getenv("CP_MOBILE_SHOTS")?.let { File(it).parent } ?: "../../ChurchPresenter-Mobile/composeApp",
).absoluteFile

internal const val DEMO_PHONE_LABEL = "Phone screen"
internal const val DEMO_SHEET_LABEL = "App sheet"
internal const val DEMO_PHONE_W = 380f
internal const val DEMO_PHONE_H = 808f
internal const val DEMO_PHONE_EDGE = 0xFF3F3F46
internal const val DEMO_SCREEN_BG = 0xFF0E0E17

/**
 * A phone: [shot] (`screenshots/<section>/<state>` or `marketing/phone/<name>`) in a rounded black
 * body. A whole screen fills it from the top; a sheet is shown by [DemoSheet] instead, over it.
 */
@Composable
internal fun DemoPhone(shot: String) {
    Box(
        Modifier.size(DEMO_PHONE_W.dp, DEMO_PHONE_H.dp)
            .clip(RoundedCornerShape(DEMO_PHONE_RADIUS_DP.dp))
            .background(Color.Black)
            .border(DEMO_PHONE_BEZEL_DP.dp, Color(DEMO_PHONE_EDGE), RoundedCornerShape(DEMO_PHONE_RADIUS_DP.dp))
            .padding(DEMO_PHONE_BEZEL_DP.dp),
    ) {
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape((DEMO_PHONE_RADIUS_DP - DEMO_PHONE_BEZEL_DP).dp))
                .background(Color(DEMO_SCREEN_BG)),
            contentAlignment = Alignment.Center,
        ) {
            phoneShot(shot)?.let { (image, whole) ->
                Image(
                    image,
                    contentDescription = DEMO_PHONE_LABEL,
                    contentScale = if (whole) ContentScale.Crop else ContentScale.Fit,
                    alignment = Alignment.TopCenter,
                    modifier = if (whole) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(DEMO_SHEET_PAD_DP.dp),
                )
            }
        }
    }
}

/**
 * A sheet of the app's — a part of a screen, or one shot on a tablet canvas — cut down to what it shows
 * and floated over the devices at a size that can be read, never wider or taller than the panel allows.
 */
@Composable
internal fun DemoSheet(shot: String) {
    if (shot.isEmpty()) return
    val image = phoneShot(shot)?.first ?: return
    val scale = minOf(SHEET_MAX_W / image.width, SHEET_MAX_H / image.height, SHEET_MAX_SCALE)
    Box(
        Modifier.clip(RoundedCornerShape(SHEET_RADIUS_DP.dp))
            .border(2.dp, Color(DEMO_PHONE_EDGE), RoundedCornerShape(SHEET_RADIUS_DP.dp))
            .background(Color(DEMO_SCREEN_BG))
            .padding(SHEET_FRAME_PAD_DP.dp),
    ) {
        Image(
            image,
            contentDescription = DEMO_SHEET_LABEL,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.size((image.width * scale).dp, (image.height * scale).dp),
        )
    }
}

/** A screen's picture, and whether it is a whole phone screen (rather than a sheet to float). */
internal fun phoneShot(shot: String): Pair<ImageBitmap, Boolean>? = phoneShots.getOrPut(shot) {
    File(MOBILE_ROOT, "${shot}__dark.png").takeIf { it.isFile }?.let { ImageIO.read(it) }?.let {
        val whole = it.width.toFloat() / it.height < DEMO_PHONE_SHAPE
        (if (whole) it else trimmed(it)).toComposeImageBitmap() to whole
    }
}

/** Whether [shot] is a sheet, shown by [DemoSheet] rather than in the phone. */
internal fun isSheet(shot: String): Boolean = phoneShot(shot)?.second == false

internal fun DemoTour.phoneScreen(): Target = panelItems(hasContentDescription(DEMO_PHONE_LABEL)).first()

internal fun DemoTour.sheetScreen(): Target = panelItems(hasContentDescription(DEMO_SHEET_LABEL)).first()

/** A box on [on] (the phone's screen, or the floating sheet), its edges as fractions of its size. */
internal fun DemoTour.screenBox(box: Rect, on: Target = phoneScreen()): Target {
    val b = on.bounds
    return Target(
        on.node,
        Rect(
            b.left + b.width * box.left,
            b.top + b.height * box.top,
            b.left + b.width * box.right,
            b.top + b.height * box.bottom,
        ),
    )
}

internal fun box(left: Float, top: Float, right: Float, bottom: Float) = Rect(left, top, right, bottom)

private val phoneShots = mutableMapOf<String, Pair<ImageBitmap, Boolean>?>()

private const val DEMO_PHONE_SHAPE = 0.6f
private const val DEMO_PHONE_RADIUS_DP = 46
private const val DEMO_PHONE_BEZEL_DP = 10
private const val DEMO_SHEET_PAD_DP = 8
private const val SHEET_MAX_W = 1100f
private const val SHEET_MAX_H = 780f
private const val SHEET_MAX_SCALE = 2.2f
private const val SHEET_RADIUS_DP = 18
private const val SHEET_FRAME_PAD_DP = 10
