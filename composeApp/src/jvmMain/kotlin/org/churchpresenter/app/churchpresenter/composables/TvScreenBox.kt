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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A TV/monitor-styled mockup of the output screen: a bezel, an inset screen area for [content],
 * and a small stand underneath. Used by settings previews that show where on the output screen
 * something (a lower third, margins, etc.) is positioned, in place of a plain rectangle.
 */
@Composable
fun TvScreenBox(
    modifier: Modifier = Modifier,
    bezelColor: Color = Color(0xFF2B2B2B),
    screenColor: Color = Color(0xFF1A1A1A),
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
