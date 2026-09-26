package org.churchpresenter.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How rounded the app's corners are, as a fraction of the radius each call site is written with.
 *
 * Every [AppShape] taking a [Dp] is scaled by this, so the whole app's roundness is this one number.
 * Percent corners are not: they are pills and circles, and stay fully round.
 *
 * The corners are plain circular ones, not squircles. A squircle was tried: every one is an arbitrary
 * path, which Skia fills, clips and shadows without the fast path it has for a rounded rectangle, and
 * animated screens drew about four times slower -- for a curve that differs by under half a pixel at
 * the three-to-nine dp these corners come to.
 */
const val CORNER_SCALE = 0.6f

/** The app's corner: [size] × [CORNER_SCALE]. Use it wherever a rounded corner is wanted. */
@Suppress("FunctionNaming") // Stands in for a constructor, as Compose's own RoundedCornerShape(...) does.
fun AppShape(size: Dp): RoundedCornerShape = RoundedCornerShape(size * CORNER_SCALE)

/** [AppShape] with a radius per corner, each × [CORNER_SCALE]. Omitted corners are square. */
@Suppress("FunctionNaming") // Stands in for a constructor, as Compose's own RoundedCornerShape(...) does.
fun AppShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): RoundedCornerShape = RoundedCornerShape(
    topStart = topStart * CORNER_SCALE,
    topEnd = topEnd * CORNER_SCALE,
    bottomEnd = bottomEnd * CORNER_SCALE,
    bottomStart = bottomStart * CORNER_SCALE,
)

/** [AppShape] with every corner [percent] of the shorter side -- not scaled, so 50 is still a pill. */
@Suppress("FunctionNaming") // Stands in for a constructor, as Compose's own RoundedCornerShape(...) does.
fun AppShape(percent: Int): RoundedCornerShape = RoundedCornerShape(percent)

/** [AppShape] with every corner [corner], used as given. */
@Suppress("FunctionNaming") // Stands in for a constructor, as Compose's own RoundedCornerShape(...) does.
fun AppShape(corner: CornerSize): RoundedCornerShape = RoundedCornerShape(corner)
