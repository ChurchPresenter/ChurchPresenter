package org.churchpresenter.lottiegen.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottiePainter
import io.github.alexzhirkevich.compottie.dynamic.LottieDynamicProperties
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.lottiegen.lottie.LottieTextShaping

/**
 * A generator preview's painter, drawing text as whole lines when [grouped] (see `TextShaping`) —
 * so what the preview shows is what the app will play.
 *
 * Letter by letter it draws the file's embedded glyph outlines, as every preview always has;
 * whole lines are drawn from [GeneratorLottieFonts].
 */
@Composable
fun rememberShapedLottiePainter(
    composition: LottieComposition?,
    progress: () -> Float,
    grouped: Boolean,
    dynamicProperties: LottieDynamicProperties? = null,
): LottiePainter = rememberLottiePainter(
    composition = composition,
    progress = progress,
    fontManager = if (grouped) GeneratorLottieFonts else null,
    dynamicProperties = dynamicProperties,
    enableTextGrouping = grouped,
)

/** [LottieTextShaping.groupsText] for [json], worked out once per file. */
@Composable
fun rememberTextGrouping(json: String): Boolean = remember(json) { LottieTextShaping.groupsText(json) }
