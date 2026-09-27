package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape

private const val DASH_ON = 4f
private const val DASH_OFF = 3f

/**
 * A dashed edge round the element -- the "+ Assign output" chip, and a value a linked profile takes
 * from its master. Drawn behind the content, following the same [AppShape] corner as a solid border.
 */
internal fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.dp.toPx(), DASH_OFF.dp.toPx())),
    )
    drawOutline(AppShape(radius).createOutline(size, layoutDirection, this), color, style = stroke)
}
