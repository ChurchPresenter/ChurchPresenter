package org.churchpresenter.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

enum class ElevationLevel(val dp: Dp) {
    Level0(0.dp),
    Level1(1.dp),
    Level2(3.dp),
    Level3(6.dp),
    Level4(8.dp),
    Level5(12.dp),
}

@Immutable
data class ShadowLayer(val offsetY: Dp, val blur: Dp, val alpha: Float)

@Immutable
data class Shadows(
    val layers: Map<ElevationLevel, List<ShadowLayer>>,
    val edge: Color,
)

private fun layers(vararg pairs: Pair<ShadowLayer, ShadowLayer>) =
    ElevationLevel.entries.drop(1).zip(pairs.map { listOf(it.first, it.second) }).toMap() +
        (ElevationLevel.Level0 to emptyList())

private fun layer(offsetY: Int, blur: Int, alpha: Float) = ShadowLayer(offsetY.dp, blur.dp, alpha)

val LightShadows = Shadows(
    layers = layers(
        layer(1, 2, 0.10f) to layer(1, 3, 0.08f),
        layer(2, 6, 0.12f) to layer(12, 28, 0.18f),
        layer(4, 10, 0.14f) to layer(18, 40, 0.25f),
        layer(6, 14, 0.16f) to layer(24, 52, 0.28f),
        layer(8, 18, 0.18f) to layer(32, 64, 0.32f),
    ),
    edge = Color(0x1F141923),
)

val DarkShadows = Shadows(
    layers = layers(
        layer(1, 2, 0.35f) to layer(1, 3, 0.25f),
        layer(2, 6, 0.35f) to layer(12, 28, 0.45f),
        layer(4, 10, 0.40f) to layer(18, 40, 0.55f),
        layer(6, 14, 0.45f) to layer(24, 52, 0.60f),
        layer(8, 18, 0.50f) to layer(32, 64, 0.65f),
    ),
    edge = Color(0x1CFFFFFF),
)

fun Modifier.elevationShadow(level: ElevationLevel, shape: Shape, elevation: Shadows): Modifier {
    val shadowed = elevation.layers.getValue(level).fold(this) { modifier, layer ->
        modifier.dropShadow(
            shape,
            Shadow(
                radius = layer.blur,
                color = Color.Black,
                offset = DpOffset(0.dp, layer.offsetY),
                alpha = layer.alpha,
            ),
        )
    }
    return if (level >= ElevationLevel.Level2) shadowed.border(1.dp, elevation.edge, shape) else shadowed
}
