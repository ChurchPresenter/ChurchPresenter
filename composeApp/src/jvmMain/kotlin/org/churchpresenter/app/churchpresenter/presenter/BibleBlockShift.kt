package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.BibleTranslationSettings

/*
 * A Bible translation's own moves: its block, and its reference on top of that -- output pixels at
 * 1080 lines, scaled as the text is, one pair per output.
 */

/** [item]'s block moved by its own Shift X / Y, so one translation of a stack is nudged alone. */
internal fun Modifier.translationShift(
    item: BibleTranslationSettings,
    lowerThird: Boolean,
    scaleFactor: Float,
): Modifier {
    val x = if (lowerThird) item.lowerThirdShiftX else item.shiftX
    val y = if (lowerThird) item.lowerThirdShiftY else item.shiftY
    return shifted(x, y, scaleFactor)
}

/** [item]'s reference moved by its own shift, on top of the block's -- see [translationShift]. */
internal fun Modifier.referenceShift(
    item: BibleTranslationSettings,
    lowerThird: Boolean,
    scaleFactor: Float,
): Modifier {
    val (x, y) = item.referenceShiftFor(lowerThird)
    return shifted(x, y, scaleFactor)
}

private fun Modifier.shifted(x: Int, y: Int, scaleFactor: Float): Modifier =
    if (x == 0 && y == 0) this else offset((x * scaleFactor).dp, (y * scaleFactor).dp)

/** The reference's own move on [lowerThird]'s output, x to y. */
internal fun BibleTranslationSettings.referenceShiftFor(lowerThird: Boolean): Pair<Int, Int> =
    if (lowerThird) lowerThirdReferenceShiftX to lowerThirdReferenceShiftY else referenceShiftX to referenceShiftY

/** [this] with its reference moved to [x], [y] on [lowerThird]'s output. */
internal fun BibleTranslationSettings.withReferenceShift(
    lowerThird: Boolean,
    x: Int,
    y: Int,
): BibleTranslationSettings =
    if (lowerThird) copy(lowerThirdReferenceShiftX = x, lowerThirdReferenceShiftY = y)
    else copy(referenceShiftX = x, referenceShiftY = y)
