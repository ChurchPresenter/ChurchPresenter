@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives the page from its **input** rather than from its controls: the settings object is replaced
 * from outside and the rendered page must follow.
 *
 * This is the direction the behaviour tests cannot cover. The address, port and slot boxes each keep
 * a `remember(...)`-keyed copy of their setting, so a box showing what was typed proves only that it
 * echoed a keystroke — it says nothing about the box re-seeding when the settings change underneath,
 * which is what a settings import does while the dialog is open.
 *
 * The fixture is [atemAllDistinct], so no box can pass by showing its neighbour's value.
 */
class AtemSettingsTabRecompositionTest {

    private fun rerenderable(
        initial: AppSettings = atemAllDistinct(),
        block: ComposeUiTest.(set: (AtemSettings.() -> AtemSettings) -> Unit) -> Unit,
    ) = runComposeUiTest {
        var state by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                AtemSettingsTab(settings = state, onSettingsChange = { transform -> state = transform(state) })
            }
        }
        block { change ->
            state = state.copy(atemSettings = state.atemSettings.change())
            waitForIdle()
        }
    }

    @Test
    fun `the page survives a recomposition that changes none of its inputs`() = rerenderable { set ->
        atemHostBox().assertShows("10.0.0.5", "the address box")
        atemFieldUnder(AtemLabel.STILL_SLOT).assertShows("5", "the still slot box")

        set { this }

        atemHostBox().assertShows("10.0.0.5", "the address box after a recomposition that changed nothing")
        atemFieldUnder(AtemLabel.STILL_SLOT).assertShows("5", "the still slot box after it")
        assertEquals("${AtemLabel.STILL_SLOT} (1–20)", captionOf(AtemLabel.STILL_SLOT), "and its range")
    }

    @Test
    fun `a stored address, port and render size reach their boxes without any interaction`() = rerenderable { set ->
        set { copy(host = "imported.local", port = 9999, renderWidth = 3840, renderHeight = 2160) }

        atemHostBox().assertShows("imported.local", "the address box after the settings changed")
        atemPortBox().assertShows("9999", "the port box after the settings changed")
        atemFieldUnder(AtemLabel.WIDTH).assertShows("3840", "the width box after the settings changed")
        atemFieldUnder(AtemLabel.HEIGHT).assertShows("2160", "the height box after the settings changed")
    }

    @Test
    fun `stored slots reach their boxes shown one-based`() = rerenderable { set ->
        set { copy(defaultStillSlot = 0, defaultClipSlot = 1, backgroundSlot1 = 2, backgroundSlot2 = 3) }

        atemFieldUnder(AtemLabel.STILL_SLOT).assertShows("1", "the still slot box, stored 0")
        atemFieldUnder(AtemLabel.CLIP_SLOT).assertShows("2", "the clip slot box, stored 1")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1).assertShows("3", "the first background slot box, stored 2")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2).assertShows("4", "the second background slot box, stored 3")
    }

    @Test
    fun `a switcher detected underneath re-ranges the boxes`() = rerenderable { set ->
        set { copy(detectedStillSlots = 8, detectedMixEffects = 2) }

        assertEquals("${AtemLabel.STILL_SLOT} (1–8)", captionOf(AtemLabel.STILL_SLOT), "the still slot range")
        assertEquals("${AtemLabel.ME} (1–2)", captionOf(AtemLabel.ME), "the M/E range")
    }

    /** The key is driven as an upstream keyer (M/E and key) or a downstream one (DSK), never both. */
    @Test
    fun `switching the downstream keyer on in settings swaps the M-E and key boxes for the DSK box`() =
        rerenderable { set ->
            assertTrue(hasFieldUnder(AtemLabel.ME) && hasFieldUnder(AtemLabel.KEY), "upstream out of the box")
            assertFalse(hasFieldUnder(AtemLabel.DSK), "and no DSK box beside it")

            set { copy(useDownstreamKey = true) }

            assertFalse(hasFieldUnder(AtemLabel.ME) || hasFieldUnder(AtemLabel.KEY), "no upstream boxes on DSK")
            atemFieldUnder(AtemLabel.DSK).assertShows("4", "the DSK box, stored 3")

            set { copy(useDownstreamKey = false) }

            assertTrue(hasFieldUnder(AtemLabel.ME) && hasFieldUnder(AtemLabel.KEY), "upstream again")
            assertFalse(hasFieldUnder(AtemLabel.DSK), "and the DSK box gone")
        }

    /**
     * The parent hands the page a new `onSettingsChange` on each recomposition, as `OptionsDialog`
     * does. A page that kept the stale one would write into a callback the parent has replaced, and
     * the write would appear to succeed while reaching nothing.
     */
    @Test
    fun `a click reaches the newest callback when the parent keeps replacing it`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        var generation by mutableStateOf(0)
        var calledGeneration = -1

        setContent {
            MaterialTheme {
                val thisGeneration = generation
                AtemSettingsTab(
                    settings = settings,
                    onSettingsChange = { transform ->
                        calledGeneration = thisGeneration
                        settings = transform(settings)
                    },
                )
            }
        }

        atemSwitchFor(AtemLabel.QUICK_UPLOAD).performClick()
        waitForIdle()
        assertEquals(0, calledGeneration, "the first callback must be the one invoked")
        assertTrue(settings.atemSettings.quickUpload, "and its write must land")

        generation = 1
        waitForIdle()

        atemSwitchFor(AtemLabel.QUICK_UPLOAD).performClick()
        waitForIdle()
        assertEquals(1, calledGeneration, "the replacement callback must be invoked, not the stale one")
        assertFalse(settings.atemSettings.quickUpload, "and its write must land too")
    }
}
