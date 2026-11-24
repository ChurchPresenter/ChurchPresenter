package org.churchpresenter.hosanna.composables.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

private fun picture(width: Float, height: Float, sky: Color, block: String): ImageVector =
    ImageVector.Builder(
        defaultWidth = width.dp,
        defaultHeight = height.dp,
        viewportWidth = width,
        viewportHeight = height,
    )
        .addPath(PathParser().parsePathString("M0,0h${width}v${height}h-${width}z").toNodes(), fill = SolidColor(sky))
        .addPath(PathParser().parsePathString(block).toNodes(), fill = SolidColor(Color(0xFFF4E3B5)))
        .build()

@Composable
fun testSlide(): Painter = rememberVectorPainter(
    picture(160f, 90f, Color(0xFF27466E), "M20,30h120v10h-120zM40,50h80v6h-80z"),
)

@Composable
fun testPortrait(): Painter = rememberVectorPainter(
    picture(90f, 160f, Color(0xFF3D6B4F), "M15,60h60v40h-60z"),
)
