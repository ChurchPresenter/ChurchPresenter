package org.churchpresenter.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object HosannaRadius {
    val XS: Dp = 4.dp
    val S: Dp = 6.dp
    val M: Dp = 8.dp
    val L: Dp = 12.dp
    val XL: Dp = 16.dp

    fun nested(inner: Dp, padding: Dp): Dp = inner + padding
}

@Immutable
data class HosannaShapes(
    val xs: Shape,
    val s: Shape,
    val m: Shape,
    val l: Shape,
    val xl: Shape,
    val full: Shape,
)

val HosannaDefaultShapes = HosannaShapes(
    xs = RoundedCornerShape(HosannaRadius.XS),
    s = RoundedCornerShape(HosannaRadius.S),
    m = RoundedCornerShape(HosannaRadius.M),
    l = RoundedCornerShape(HosannaRadius.L),
    xl = RoundedCornerShape(HosannaRadius.XL),
    full = CircleShape,
)
