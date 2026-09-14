package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/**
 * A template at rest — its hold frame, with the sample text it was generated with — for the
 * Background tab's stage. A file that is missing or is not a template draws nothing.
 */
@Composable
internal fun BibleLottieStillFrame(path: String, modifier: Modifier = Modifier) {
    val template by rememberBibleLottieTemplate(path)
    val loaded = template ?: return
    val composition by rememberLottieComposition(loaded.json) { LottieCompositionSpec.JsonString(loaded.json) }
    // No font manager: the sample is drawn from the glyph outlines the generator embedded, which
    // is exactly how the generator's own preview draws it, so the two agree.
    val painter = rememberLottiePainter(
        composition = composition,
        progress = { loaded.progressAt(BibleBandClock()) },
    )
    Image(painter = painter, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = modifier)
}
