@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rest of the ATEM upload dialog's content: the design-vs-switcher size warnings, the slot
 * dropdown's own selection, the fps/capacity readout, and the quick-upload shortcut buttons that
 * replace the dialog entirely when configured.
 *
 * See `LowerThirdAtemDialogTest.kt` for why the switcher state can be injected at all, and
 * `LowerThirdTabTestSupport.kt` for `openAtemDialog()`.
 */
class LowerThirdAtemDialogExtraTest {

    private fun state(
        fps: Double = 25.0,
        stills: List<AtemMediaSlot> = listOf(
            AtemMediaSlot(index = 0, name = "Welcome", isUsed = true),
            AtemMediaSlot(index = 1, name = "", isUsed = false),
        ),
        clips: List<AtemMediaSlot> = listOf(AtemMediaSlot(index = 0, name = "Clip A", isUsed = true)),
        clipMaxFrames: List<Int> = listOf(600),
    ) = AtemState(
        fps = fps,
        videoMode = "1080p25",
        stillSlots = stills,
        clipSlots = clips,
        clipMaxFrames = clipMaxFrames,
    )

    /** Square, and bigger than the switcher's 1920×1080 raster on every axis — an aspect mismatch
     *  with no upscaling, so it isolates the mismatch banner from the upscale one. */
    private val mismatchedLottie =
        """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":2000,"h":2000,"layers":[]}"""

    /** Same 16:9 aspect as the switcher's raster, just smaller — isolates the upscale banner. */
    private val undersizedLottie =
        """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":960,"h":540,"layers":[]}"""

    @Test
    fun `a design whose aspect ratio does not match the switcher is warned about`() =
        lowerThirdTab(
            folder = lottieFolderWithContent("Mismatched" to mismatchedLottie),
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
        ) { _ ->
            openAtemDialog("Mismatched")

            assertTrue(
                showsContainingText("2000×2000"),
                "the design's own size must be named in the mismatch warning: ${renderedText()}",
            )
        }

    @Test
    fun `an undersized design triggers the upscale notice`() =
        lowerThirdTab(
            folder = lottieFolderWithContent("Small" to undersizedLottie),
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
        ) { _ ->
            openAtemDialog("Small")

            assertTrue(
                showsContainingText("960×540"),
                "the undersized design's dimensions must be named in the upscale notice: ${renderedText()}",
            )
        }

    @Test
    fun `a clip too long for the slot blocks the upload`() =
        lowerThirdTab(
            atemReachable = true,
            // The fixture is 2s at 30fps = 60 frames; a 10-frame slot can never fit it.
            queryAtemState = { _, _ -> state(clipMaxFrames = listOf(10)) },
        ) { _ ->
            openAtemDialog()
            onAllNodes(isSelectable())[1].performClick()
            waitForIdle()

            onNodeWithText("Upload").assertIsNotEnabled()
        }

    @Test
    fun `the detected fps is shown once a clip pool is loaded`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state(fps = 25.0) },
        ) { _ ->
            openAtemDialog()
            onAllNodes(isSelectable())[1].performClick()
            waitForIdle()

            assertTrue(showsContainingText("25 fps"), "got ${renderedText()}")
        }

    // Not tested: picking a different slot from the dropdown (`ExposedDropdownMenuBox` at
    // LowerThird.kt:594). Clicking the read-only anchor field — both a plain performClick() and a
    // performMouseInput() aimed at the trailing-icon corner — never expands the menu under
    // synthetic input, and there is no precedent anywhere in the suite for driving this exact
    // Material3 component. Same class of gap as the `Modifier.draggable` resize handles elsewhere
    // in this tab and in BibleTab/PresentationTab.

    @Test
    fun `switching back to still mode re-queries and lists the still slots again`() {
        val queried = mutableListOf<String>()
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { host, _ -> queried += host; state() },
        ) { _ ->
            openAtemDialog()
            onAllNodes(isSelectable())[1].performClick()
            waitForIdle()
            val afterClip = queried.size

            onAllNodes(isSelectable())[0].performClick()
            waitForIdle()

            assertTrue(queried.size > afterClip, "switching back to still must re-read the pool too")
            assertTrue(showsContainingText("Welcome"), "the still pool's occupied slot is listed again")
        }
    }

    @Test
    fun `quick upload is a pair of one-click buttons instead of the dialog`() =
        lowerThirdTab(
            atemReachable = true,
            quickUpload = true,
            queryAtemState = { _, _ -> state() },
        ) { _ ->
            selectPreset("Welcome")
            waitForIdle()

            assertFalse(hasLtButton(ATEM_UPLOAD_LABEL), "quick upload replaces the single dialog-opening button")
        }
}
