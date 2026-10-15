@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The TV-shaped frame the settings pages draw an output's preview in. */
class TvScreenBoxTest {

    @Test
    fun `a TV box with a ratio draws its screen in that shape, on either theme`() = runComposeUiTest {
        var dark by mutableStateOf(false)
        setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.width(320.dp)) {
                    TvScreenBox(screenAspectRatio = 2f) { Box(Modifier.fillMaxSize().testTag("screen")) }
                }
            }
        }
        listOf(false, true).forEach { theme ->
            dark = theme
            waitForIdle()
            val bounds = onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
            assertTrue(abs(bounds.width / bounds.height - 2f) < 0.05f, "screen is 2:1 (dark=$theme)")
        }
    }

    @Test
    fun `a TV box without a ratio fills the height it is given`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.width(300.dp).height(200.dp)) {
                    TvScreenBox(
                        modifier = Modifier.testTag("tv"),
                        bezelColor = Color.DarkGray,
                        screenColor = Color.Black,
                    ) { Box(Modifier.fillMaxSize().testTag("screen")) }
                }
            }
        }
        val tv = onNodeWithTag("tv").fetchSemanticsNode().boundsInRoot
        val screen = onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        assertTrue(screen.height > 0f && screen.height < tv.height)
        assertTrue(screen.width < tv.width)
    }
}
