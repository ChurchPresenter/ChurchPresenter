package org.churchpresenter.sharedui.presenter

import androidx.compose.ui.unit.Dp

/** The output width every stored background size is measured against. */
const val BACKGROUND_REFERENCE_WIDTH = 1920f

/** The output height every stored size is measured against, as [BACKGROUND_REFERENCE_WIDTH] is. */
const val REFERENCE_HEIGHT = 1080f

/**
 * The range a presenter's scale is held to, so an absurd output cannot produce absurd type.
 *
 * [MIN_PRESENTER_SCALE] used to be 0.5 -- comfortably below every 16:9-family preset
 * ([org.churchpresenter.app.churchpresenter.utils.OutputGeometry], 1280x720 up), so nobody noticed it
 * was also above the ratio a genuinely narrow output computes. `presenterScale` takes the *smaller*
 * of the width and height ratio against the 1920x1080 reference specifically so a mismatched aspect
 * ratio is respected; flooring that result at 0.5 threw the answer away for anything narrower than
 * that, which every vertical/mobile output is. A 720x1280 target computed 0.375 and was floored up to
 * 0.5 -- 33% larger than the space actually available -- which is what let lyrics overflow a portrait
 * output while a landscape one of any shipped size never showed the bug.
 *
 * 0.15 is chosen against [org.churchpresenter.app.churchpresenter.composables.RESOLUTION_RANGE]'s own
 * floor of 16: a width or height of 16 against the 1920x1080 reference computes a ratio near 0.008,
 * far below any legible floor, so a genuinely pathological output (not merely narrow, but tiny) still
 * needs a floor to keep type from vanishing -- it will still overflow that output, same as it always
 * has, because there is no scale that both fits 16px and stays readable. Every realistic vertical
 * preset and custom resolution sits above 0.15, so the floor no longer fires for them.
 */
const val MIN_PRESENTER_SCALE = 0.15f
const val MAX_PRESENTER_SCALE = 3.0f

/**
 * The factor the song and Bible presenters multiply every stored size by: type, margins, window
 * insets, shadows and the gaps between blocks, all of which are authored against a 1920x1080
 * output and drawn on one [width] by [height].
 *
 * Takes dp and not pixels, for the reason `backgroundBlurRadius` states. Everything it scales is
 * applied as `.dp` or `.sp`, which the platform already multiplies by the output's density, so
 * measuring the output in pixels counted that density a second time and a HiDPI output -- a Retina
 * Mac, or Windows at 150% -- drew everything `density` times too large. Auto-fit was not the part
 * that was wrong: it computes in the 1920x1080 reference space and picked a size that genuinely
 * fitted, which was then drawn at twice that and ran off the side of the screen.
 *
 * The two presenters had this inline and identical; it is one function so it can be checked
 * directly rather than only through a rendered screen.
 */
fun presenterScale(width: Dp, height: Dp): Float = minOf(
    width.value / BACKGROUND_REFERENCE_WIDTH,
    height.value / REFERENCE_HEIGHT,
).coerceIn(MIN_PRESENTER_SCALE, MAX_PRESENTER_SCALE)
