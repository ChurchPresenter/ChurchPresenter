@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.sharedui.utils.FALLBACK_STAGE_ASPECT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.churchpresenter.media.data.StockMediaClient
import org.churchpresenter.canvas.CameraDevice
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The order the library offers its tiles in: the two tiles that are not a fixed choice — Custom
 * color and Browse… — lead their grids rather than trailing a list that has to be scrolled first.
 */
class SongBackgroundLibraryTest {

    private fun library(
        background: SongBackground = SongBackground(),
        devices: List<CameraDevice> = emptyList(),
        onChange: (SongBackground) -> Unit = {},
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(LIBRARY_WIDTH, LIBRARY_HEIGHT)) {
                    SongBackgroundLibrary(
                        background = background,
                        onChange = onChange,
                        swatchAspect = FALLBACK_STAGE_ASPECT,
                        devices = devices,
                    )
                }
            }
        }
        block()
    }

    /** Asserts [first] is drawn before [second] in reading order. */
    private fun ComposeUiTest.assertLeads(first: String, second: String) {
        val lead = onNodeWithText(first).getUnclippedBoundsInRoot()
        val rest = onNodeWithText(second).getUnclippedBoundsInRoot()
        assertTrue(
            lead.top < rest.top || (lead.top == rest.top && lead.left < rest.left),
            "$first must come before $second, but sat at ${lead.left},${lead.top} against ${rest.left},${rest.top}",
        )
    }

    @Test
    fun `the custom color tile leads the colors grid`() = library {
        assertLeads(CUSTOM, FIRST_NAMED_COLOR)
    }

    @Test
    fun `the custom tile is the only one of its kind, and it is first`() {
        assertTrue(SONG_BACKGROUND_COLORS.first().own, "the custom tile leads")
        assertEquals(1, SONG_BACKGROUND_COLORS.count { it.own })
    }

    @Test
    fun `Browse leads the pictures grid`() = library {
        onNodeWithText(IMAGES).performClick()
        waitUntil(timeoutMillis = BUNDLED_TIMEOUT_MS) {
            onAllNodesWithText(firstStockPicture).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        assertLeads(BROWSE, firstStockPicture)
    }

    /** Nothing is bundled for clips, so Browse… is the whole grid — and still has to be in it. */
    @Test
    fun `Browse is offered for clips too`() = library {
        onNodeWithText(VIDEOS).performClick()
        waitForIdle()
        onNodeWithText(BROWSE).assertIsDisplayed()
    }

    @Test
    fun `a picture or clip chosen by browsing is named on the Browse tile`() {
        val picture = SongBackground(type = SongBackgroundType.IMAGE, image = "/elsewhere/sunrise.png")
        library(background = picture) {
            onNodeWithText("sunrise.png").assertIsDisplayed()
            assertTrue(onAllNodesWithText(BROWSE).fetchSemanticsNodes().isEmpty())
        }
        val clip = SongBackground(type = SongBackgroundType.VIDEO, video = "/elsewhere/waves.mp4")
        library(background = clip) {
            onNodeWithText("waves.mp4").assertIsDisplayed()
        }
    }

    @Test
    fun `the cameras tab offers each camera but no display, marks the chosen one, and a tap picks another`() {
        val webcam = CameraDevice("FaceTime HD Camera", "avfoundation://0", "FaceTime HD Camera")
        val card = CameraDevice(
            "DeckLink Mini", "decklink://0", "DeckLink: DeckLink Mini", isDeckLink = true, deckLinkIndex = 0,
        )
        val screen = CameraDevice("Capture screen 0", "avfoundation://1", "Capture screen 0")
        val current = cameraBackground(SongBackground(), webcam)
        val changes = mutableListOf<SongBackground>()
        library(background = current, devices = listOf(webcam, card, screen), onChange = { changes += it }) {
            onNodeWithText(webcam.displayName).assertIsDisplayed()
            assertTrue(onAllNodesWithText(screen.displayName).fetchSemanticsNodes().isEmpty())

            // The name is a caption under the swatch; the swatch above it is what takes the tap.
            onNodeWithText(card.displayName).performTouchInput { click(Offset(width / 2f, -SWATCH_ABOVE_CAPTION)) }
            waitForIdle()
        }
        assertTrue(changes.single().camera.isDeckLink)
        assertEquals(0, changes.single().camera.deckLinkIndex)
    }

    @Test
    fun `a stock picture tapped becomes the background`() {
        val changes = mutableListOf<SongBackground>()
        library(onChange = { changes += it }) {
            onNodeWithText(IMAGES).performClick()
            waitUntil(timeoutMillis = BUNDLED_TIMEOUT_MS) {
                onAllNodesWithText(firstStockPicture).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            onNodeWithText(firstStockPicture).performTouchInput { click(Offset(width / 2f, -SWATCH_ABOVE_CAPTION)) }
            waitUntil(timeoutMillis = BUNDLED_TIMEOUT_MS) { changes.isNotEmpty() }
        }
        assertEquals(SongBackgroundType.IMAGE, changes.last().type)
        assertTrue(changes.last().image.isNotBlank())
    }

    private companion object {
        val LIBRARY_WIDTH = 448.dp
        val LIBRARY_HEIGHT = 560.dp

        const val CUSTOM = "Custom color"
        const val FIRST_NAMED_COLOR = "Solid Black"
        const val BROWSE = "Browse…"
        const val IMAGES = "Images"
        const val VIDEOS = "Videos"
        const val BUNDLED_TIMEOUT_MS = 5_000L
        const val SWATCH_ABOVE_CAPTION = 12f

        /**
         * The tile Browse… has to sit before, taken from the library itself rather than named here:
         * `user.home` is per fork and empty, so the grid is the bundled set in its own order.
         */
        val firstStockPicture: String by lazy {
            val bundled = runBlocking { loadBundledFileNames(StockMediaClient.StockMediaType.PHOTO) }
            libraryEntries(emptyList(), bundled, "").first().name
        }
    }
}
