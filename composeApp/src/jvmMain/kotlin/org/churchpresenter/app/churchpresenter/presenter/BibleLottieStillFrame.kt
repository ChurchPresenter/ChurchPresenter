package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.ExperimentalCompottieApi
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.dynamic.rememberLottieDynamicProperties
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/**
 * A template at rest — its hold frame, with the sample text it was generated with — for the
 * Background tab's stage. With a [look] the band is drawn under its overlay and the sample text
 * over it, the way the output draws them. A file that is missing or is not a template draws nothing.
 */
@OptIn(ExperimentalCompottieApi::class)
@Composable
internal fun BibleLottieStillFrame(path: String, modifier: Modifier = Modifier, look: BandLook? = null) {
    val template by rememberBibleLottieTemplate(path)
    val loaded = template ?: return
    val composition by rememberLottieComposition(loaded.json) { LottieCompositionSpec.JsonString(loaded.json) }
    val progress = { loaded.progressAt(BibleBandClock()) }
    if (look == null) {
        StillFramePass(loaded, composition, progress, colorFilter = null, hide = emptySet(), modifier = modifier)
        return
    }
    val bandLayers = loaded.layerNames.filter { it.startsWith(BibleLottieTemplate.BAND_PREFIX) }.toSet()
    val textLayers = loaded.layerNames.filter { it !in bandLayers }.toSet()
    Box(modifier) {
        BandBackdrop(look, Modifier.fillMaxSize()) { backdropModifier ->
            StillFramePass(loaded, composition, progress, look.dimFilter(), hide = textLayers, backdropModifier)
        }
        StillFramePass(loaded, composition, progress, colorFilter = null, hide = bandLayers, Modifier.fillMaxSize())
    }
}

/** One pass of the still frame with the named layers hidden. */
@OptIn(ExperimentalCompottieApi::class)
@Composable
private fun StillFramePass(
    template: BibleLottieTemplate,
    composition: LottieComposition?,
    progress: () -> Float,
    colorFilter: ColorFilter?,
    hide: Set<String>,
    modifier: Modifier,
) {
    val dynamic = rememberLottieDynamicProperties(template, hide) {
        hide.forEach { name -> layer(name) { hidden { true } } }
    }
    // No font manager: the sample is drawn from the glyph outlines the generator embedded, which
    // is exactly how the generator's own preview draws it, so the two agree.
    val painter = rememberLottiePainter(
        composition = composition,
        progress = progress,
        dynamicProperties = dynamic,
    )
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = colorFilter,
        modifier = modifier,
    )
}
