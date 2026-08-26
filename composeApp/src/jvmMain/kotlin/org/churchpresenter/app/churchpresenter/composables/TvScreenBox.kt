package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

private const val LIGHT_THEME_BEZEL_COLOR = 0xFF2B2B2B
private const val DARK_THEME_BEZEL_COLOR = 0xFF585858
private const val DEFAULT_SCREEN_COLOR = 0xFF1A1A1A

/** Below this, the surface behind the mockup counts as a dark one. */
private const val DARK_SURFACE_LUMINANCE = 0.5f

/**
 * The bezel colour, light enough to read against the surface behind it.
 *
 * One fixed dark grey worked on the light themes and disappeared on the dark ones, where the panel
 * it sits on is nearly as dark as the bezel was: the mock TV blended into the background and read as
 * a flat rectangle with no screen in it.
 */
@Composable
private fun defaultBezelColor(): Color = Color(
    if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE) DARK_THEME_BEZEL_COLOR
    else LIGHT_THEME_BEZEL_COLOR
)

/**
 * A TV/monitor-styled mockup of the output screen: a bezel, an inset screen area for [content],
 * and a small stand underneath. Used by settings previews that show where on the output screen
 * something (a lower third, margins, etc.) is positioned, in place of a plain rectangle.
 */
@Composable
fun TvScreenBox(
    modifier: Modifier = Modifier,
    bezelColor: Color = defaultBezelColor(),
    screenColor: Color = Color(DEFAULT_SCREEN_COLOR),
    content: @Composable BoxScope.() -> Unit = {}
) {
    // `modifier` (which may carry a RowScope/ColumnScope weight from the caller) must land on this
    // outermost node — a weight buried on an inner child has no effect on the caller's layout.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(bezelColor, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(screenColor, RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
                content = content
            )
        }
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(10.dp)
                .background(bezelColor)
        )
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(6.dp)
                .background(bezelColor, RoundedCornerShape(3.dp))
        )
    }
}
