@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the ATEM tab puts on screen before anything is touched: the three cards, everything that is
 * only ever read, and the full inventory of controls.
 *
 * The controls themselves are driven elsewhere, one class per group —
 * [AtemSettingsTabConnectionTest] (host, port, render size, fps, Test Connection),
 * [AtemSettingsTabSlotsTest] (the four media-pool slots), [AtemSettingsTabKeyTest] (M/E, keyer, DSK
 * and the roll margins), [AtemSettingsTabTogglesTest] (the three switches) and
 * [AtemSettingsTabDetectedTest] (the two read-only lines that report what the last Test Connection
 * found).
 *
 * The inventory test below is deliberately a count: it fails when a control is added or dropped, which
 * is the one thing per-control tests cannot notice.
 */
class AtemSettingsTabTest {

    @Test
    fun `the tab is three cards`() = atemTab { _ ->
        onNodeWithText(AtemLabel.SECTION_CONNECTION).assertExists("the connection card")
        onNodeWithText(AtemLabel.SECTION_LOWER_THIRD).assertExists("the lower-third uploads card")
        onNodeWithText(AtemLabel.SECTION_BACKGROUNDS).assertExists("the background uploads card")
    }

    @Test
    fun `the connection card explains itself`() = atemTab { _ ->
        onNodeWithText(AtemLabel.DESCRIPTION).assertExists("what the ATEM integration is for")
        onNodeWithText(AtemLabel.TEST_HINT).assertExists("what Test Connection does")
        onNodeWithText(AtemLabel.HOST_ROW).assertExists("the IP address row caption")
        onNodeWithText(AtemLabel.RESOLUTION).assertExists("the render resolution caption")
    }

    @Test
    fun `every switch is captioned and explained`() = atemTab { _ ->
        onNodeWithText(AtemLabel.DSK_SWITCH).assertExists("the DSK switch caption")
        onNodeWithText(AtemLabel.DSK_SWITCH_HINT).assertExists("the DSK switch hint")
        onNodeWithText(AtemLabel.QUICK_UPLOAD).assertExists("the quick-upload switch caption")
        onNodeWithText(AtemLabel.QUICK_UPLOAD_HINT).assertExists("the quick-upload switch hint")
        onNodeWithText(AtemLabel.GO_LIVE_KEY).assertExists("the go-live-key switch caption")
        onNodeWithText(AtemLabel.GO_LIVE_KEY_HINT).assertExists("the go-live-key switch hint")
    }

    /**
     * Out of the box the key is upstream, so the M/E and keyer boxes are on screen and the DSK box is
     * not — the row holds one or the other, never both.
     */
    @Test
    fun `the tab offers thirteen boxes, three switches and one button`() = atemTab { _ ->
        assertEquals(
            13,
            atemBoxes().fetchSemanticsNodes().size,
            "host, port, width, height, fps, still slot, clip slot, M/E, key, pre-roll, post-roll, " +
                "background slot 1, background slot 2",
        )
        assertEquals(3, atemSwitches().fetchSemanticsNodes().size, "DSK, quick upload, go-live key")
        onNodeWithText(AtemLabel.TEST).assertExists("the Test Connection button")
    }

    @Test
    fun `nothing is switched on out of the box`() = atemTab { get ->
        val atem = get().atemSettings
        assertTrue(
            !atem.useDownstreamKey && !atem.quickUpload && !atem.goLiveKey,
            "all three broadcast switches start off",
        )
        atemSwitchFor(AtemLabel.DSK_SWITCH).assertIsOff()
        atemSwitchFor(AtemLabel.QUICK_UPLOAD).assertIsOff()
        atemSwitchFor(AtemLabel.GO_LIVE_KEY).assertIsOff()
    }

    /** Every box shows what the settings hold, and the 0-based ones show one more than they store. */
    @Test
    fun `the boxes are filled from the settings`() = atemTab(initial = atemAllDistinct()) { get ->
        val atem = get().atemSettings

        atemHostBox().assertShows(atem.host, "the IP address box")
        atemPortBox().assertShows("9911", "the port box")
        atemFieldUnder(AtemLabel.WIDTH).assertShows("1280", "the render width box")
        atemFieldUnder(AtemLabel.HEIGHT).assertShows("720", "the render height box")
        atemFieldUnder(AtemLabel.FPS).assertShows("25", "the clip fps box")
        atemFieldUnder(AtemLabel.PRE_ROLL).assertShows("150", "the key pre-roll box")
        atemFieldUnder(AtemLabel.POST_ROLL).assertShows("250", "the key post-roll box")

        atemFieldUnder(AtemLabel.STILL_SLOT).assertShows("5", "still slot 4, shown 1-based")
        atemFieldUnder(AtemLabel.CLIP_SLOT).assertShows("7", "clip slot 6, shown 1-based")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1).assertShows("9", "background slot 8, shown 1-based")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2).assertShows("11", "background slot 10, shown 1-based")
        atemFieldUnder(AtemLabel.ME).assertShows("2", "M/E 1, shown 1-based")
        atemFieldUnder(AtemLabel.KEY).assertShows("3", "keyer 2, shown 1-based")
    }

    /** The defaults an unconfigured install carries, as the operator first sees them. */
    @Test
    fun `an unconfigured tab shows the shipped defaults`() = atemTab { _ ->
        atemHostBox().assertShows("", "the IP address box, which ships empty")
        atemPortBox().assertShows("9910", "the ATEM control port")
        atemFieldUnder(AtemLabel.WIDTH).assertShows("1920", "the default render width")
        atemFieldUnder(AtemLabel.HEIGHT).assertShows("1080", "the default render height")
        atemFieldUnder(AtemLabel.FPS).assertShows("30", "the default clip fps, formatted without a decimal")
        atemFieldUnder(AtemLabel.STILL_SLOT).assertShows("1", "still slot 0, shown 1-based")
        atemFieldUnder(AtemLabel.CLIP_SLOT).assertShows("1", "clip slot 0, shown 1-based")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1).assertShows("2", "background slot 1, shown 1-based")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2).assertShows("3", "background slot 2, shown 1-based")
        atemFieldUnder(AtemLabel.PRE_ROLL).assertShows("300", "the default key pre-roll")
        atemFieldUnder(AtemLabel.POST_ROLL).assertShows("300", "the default key post-roll")
    }

    /** Rendering the tab must not itself write anything back. */
    @Test
    fun `merely showing the tab changes no setting`() = atemTab { get ->
        assertEquals(AppSettings().atemSettings, get().atemSettings, "the tab must not write on first draw")
    }
}
