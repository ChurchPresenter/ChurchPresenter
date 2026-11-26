package org.churchpresenter.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppRadius {
    val XS: Dp = 4.dp
    val S: Dp = 6.dp
    val M: Dp = 8.dp
    val L: Dp = 12.dp
    val XL: Dp = 16.dp

    fun nested(inner: Dp, padding: Dp): Dp = inner + padding
}

@Immutable
data class AppShapes(
    val xs: Shape,
    val s: Shape,
    val m: Shape,
    val l: Shape,
    val xl: Shape,
    val full: Shape,
)

val DefaultAppShapes = AppShapes(
    xs = RoundedCornerShape(AppRadius.XS),
    s = RoundedCornerShape(AppRadius.S),
    m = RoundedCornerShape(AppRadius.M),
    l = RoundedCornerShape(AppRadius.L),
    xl = RoundedCornerShape(AppRadius.XL),
    full = CircleShape,
)
