@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.OutputStyleScope
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProfilesKitComposablesTest {

    @Test
    fun `a background chip names the surface its output shape writes`() {
        assertEquals(BackgroundScope.BIBLE, CustomizeElement.BACKGROUND_BIBLE.backgroundScope(lowerThird = false))
        assertEquals(
            BackgroundScope.BIBLE_LOWER_THIRD,
            CustomizeElement.BACKGROUND_BIBLE.backgroundScope(lowerThird = true),
        )
        assertEquals(BackgroundScope.SONG, CustomizeElement.BACKGROUND_SONG.backgroundScope(lowerThird = false))
        assertEquals(
            BackgroundScope.SONG_LOWER_THIRD,
            CustomizeElement.BACKGROUND_SONG.backgroundScope(lowerThird = true),
        )
        assertEquals(BackgroundScope.DEFAULT, CustomizeElement.BACKGROUND_DEFAULT.backgroundScope(lowerThird = false))
        assertEquals(
            BackgroundScope.DEFAULT_LOWER_THIRD,
            CustomizeElement.SONG_LYRICS.backgroundScope(lowerThird = true),
        )
    }

    @Test
    fun `every element chip has its own label, and a background chip follows the output's shape`() {
        val full = mutableMapOf<CustomizeElement, String>()
        val band = mutableMapOf<CustomizeElement, String>()
        runComposeUiTest {
            setContent {
                CustomizeElement.entries.forEach { full[it] = it.label() }
                CompositionLocalProvider(LocalOutputStyleScope provides OutputStyleScope.LOWER_THIRD) {
                    CustomizeElement.entries.forEach { band[it] = it.label() }
                }
            }
            waitForIdle()
        }
        assertEquals(CustomizeElement.entries.size, full.values.distinct().size)
        assertTrue(full.values.all { it.isNotBlank() })
        listOf(CustomizeElement.BACKGROUND_DEFAULT, CustomizeElement.BACKGROUND_BIBLE, CustomizeElement.BACKGROUND_SONG)
            .forEach { assertNotEquals(full[it], band[it], it.name) }
        assertEquals(full[CustomizeElement.SONG_TITLE], band[CustomizeElement.SONG_TITLE])
    }

    @Test
    fun `a settings card shows its subtitle and header badges only when given them`() = runComposeUiTest {
        var withExtras by mutableStateOf(true)
        var tick by mutableIntStateOf(0)
        setContent {
            MaterialTheme {
                Text("tick $tick")
                if (withExtras) {
                    SettingsCard(
                        title = "Folders",
                        modifier = Modifier.testTag("card"),
                        subtitle = "5 linked",
                        headerTrailing = { SettingsCardBadge("1 needs attention", Color.Red, Color.White) },
                    ) { Text("body") }
                } else {
                    SettingsCard(title = "Folders") { Text("body") }
                }
            }
        }
        onNodeWithText("5 linked").assertExists()
        onNodeWithText("1 needs attention").assertExists()
        tick++
        waitForIdle()
        onNodeWithText("Folders").assertExists()
        withExtras = false
        waitForIdle()
        onNodeWithText("5 linked").assertDoesNotExist()
        onNodeWithText("1 needs attention").assertDoesNotExist()
        onNodeWithText("body").assertExists()
    }

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

    @Test
    fun `the song background panel sits under its chip when there is room`() {
        val anchor = IntRect(left = 300, top = 100, right = 400, bottom = 130)
        val at = SongBackgroundPanelPosition.calculatePosition(
            anchor, IntSize(1000, 900), LayoutDirection.Ltr, IntSize(250, 300),
        )
        assertEquals(400 - 250, at.x)
        assertEquals(130 + 6, at.y)
    }

    @Test
    fun `the song background panel is pulled back on screen and lifted above a chip near the bottom`() {
        val anchor = IntRect(left = 0, top = 700, right = 100, bottom = 730)
        val at = SongBackgroundPanelPosition.calculatePosition(
            anchor, IntSize(1000, 900), LayoutDirection.Ltr, IntSize(250, 300),
        )
        assertEquals(12, at.x, "pulled right to the edge pad")
        assertEquals(900 - 300 - 12, at.y, "lifted, but no higher than it needs to be to fit")
    }

    @Test
    fun `a panel taller than the window keeps to the top pad`() {
        val anchor = IntRect(left = 500, top = 50, right = 600, bottom = 80)
        val at = SongBackgroundPanelPosition.calculatePosition(
            anchor, IntSize(700, 200), LayoutDirection.Ltr, IntSize(800, 400),
        )
        assertEquals(12, at.x)
        assertEquals(12, at.y)
    }
}
