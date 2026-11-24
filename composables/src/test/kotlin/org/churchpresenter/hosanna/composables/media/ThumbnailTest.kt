@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.media

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import org.churchpresenter.hosanna.composables.model.ThumbnailFileLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailSize
import org.churchpresenter.hosanna.composables.model.ThumbnailStatus
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testSlide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ThumbnailTest {

    private val labels = ThumbnailLabels(
        "Go live", "Hide from show", "Show in the show again", "Hidden · skipped when presenting", "LIVE",
        ThumbnailFileLabels("File not found", "Locate…", "Offline"),
    )
    private val events = mutableListOf<String>()

    @Composable
    private fun Tile(
        caption: String = "Welcome",
        size: ThumbnailSize = ThumbnailSize.Medium,
        status: ThumbnailStatus = ThumbnailStatus.Ready,
        hidden: Boolean = false,
        selected: Boolean = false,
        live: Boolean = false,
    ) {
        Thumbnail(
            testSlide(), { events += "select" }, labels,
            size = size, caption = caption, subtitle = "Sermon.pptx", selected = selected, live = live,
            number = "3", duration = "3:34", status = status, hidden = hidden,
            onGoLive = { events += "live" },
            onHiddenChange = { events += "hidden=$it" },
            onLocate = { events += "locate" },
        )
    }

    private fun ComposeUiTest.show(content: @Composable () -> Unit) {
        mainClock.autoAdvance = false
        setContent { HosannaSurface { content() } }
        wait(100)
    }

    private fun ComposeUiTest.wait(ms: Long) {
        mainClock.advanceTimeBy(ms)
        waitForIdle()
    }

    private fun ComposeUiTest.hover(name: String = "Welcome") {
        onNodeWithContentDescription(name).performMouseInput { moveTo(center) }
        wait(600)
    }

    @Test
    fun `a click selects and a double click goes live`() = runComposeUiTest {
        show { Tile() }
        onNodeWithContentDescription("Welcome").performMouseInput { click(center) }
        wait(600)
        onNodeWithContentDescription("Welcome").performMouseInput { doubleClick(center) }
        wait(600)
        assertEquals(listOf("select", "live"), events)
    }

    @Test
    fun `selection is announced`() = runComposeUiTest {
        show {
            Row {
                Tile("One", selected = true)
                Tile("Two")
            }
        }
        onNodeWithContentDescription("One").assertIsSelected()
        onNodeWithContentDescription("Two").assertIsNotSelected()
    }

    @Test
    fun `hover offers hide and go live`() = runComposeUiTest {
        show { Tile() }
        onNodeWithContentDescription("Go live").assertDoesNotExist()
        hover()
        onNodeWithContentDescription("Go live").performClick()
        onNodeWithContentDescription("Hide from show").performClick()
        wait(50)
        assertEquals(listOf("live", "hidden=true"), events)
    }

    @Test
    fun `a hidden item keeps its show button and cannot go live`() = runComposeUiTest {
        show { Tile(hidden = true) }
        onNodeWithText("Hidden · skipped when presenting").assertExists()
        onNodeWithContentDescription("Show in the show again").performClick()
        onNodeWithContentDescription("Welcome").performMouseInput { doubleClick(center) }
        wait(600)
        hover()
        onNodeWithContentDescription("Go live").assertDoesNotExist()
        assertEquals("hidden=false", events.first())
        assertFalse("live" in events)
    }

    @Test
    fun `Shift Enter goes live from the keyboard`() = runComposeUiTest {
        show { Tile() }
        onNodeWithContentDescription("Welcome").requestFocus()
        onNodeWithContentDescription("Welcome").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        wait(50)
        assertEquals(listOf("live"), events)
    }

    @Test
    fun `badges show on medium, small keeps only the live dot`() = runComposeUiTest {
        show {
            Row {
                Tile("Big", live = true)
                Tile("Small", size = ThumbnailSize.Small, live = true)
            }
        }
        onAllNodesWithText("3:34").assertCountEquals(1)
        onAllNodesWithText("LIVE").assertCountEquals(1)
        hover("Small")
        onNodeWithContentDescription("Go live").assertDoesNotExist()
    }

    @Test
    fun `a missing file says so and offers Locate on hover`() = runComposeUiTest {
        show { Tile(status = ThumbnailStatus.Missing) }
        onNodeWithText("File not found").assertExists()
        hover()
        onNodeWithText("Locate…").performClick()
        wait(50)
        assertEquals(listOf("locate"), events)
    }

    @Test
    fun `an offline screen is labelled and a loading one cannot go live`() = runComposeUiTest {
        show {
            Row {
                Tile("Lobby", status = ThumbnailStatus.Offline)
                Tile("Hall", status = ThumbnailStatus.Loading)
            }
        }
        onNodeWithText("Offline").assertExists()
        onNodeWithContentDescription("Hall").performMouseInput { doubleClick(center) }
        wait(600)
        assertFalse("live" in events)
    }

    @Test
    fun `a cut off caption shows in full on hover`() = runComposeUiTest {
        val long = "42 Great Is Thy Faithfulness (with the extra verse)"
        show { Tile(long) }
        onNodeWithText(long).performMouseInput { moveTo(center) }
        wait(600)
        onAllNodesWithText(long).assertCountEquals(2)
    }
}
