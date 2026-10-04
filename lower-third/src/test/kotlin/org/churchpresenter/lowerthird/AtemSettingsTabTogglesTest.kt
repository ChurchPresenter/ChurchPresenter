@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three broadcast switches, one test each.
 *
 * They look alike and sit in a column, which is exactly why they are driven separately: the failure
 * they are guarding against is one switch writing another's flag. Every test therefore asserts the
 * flag it expects **and** that the other two are still off, and reads the switch back to confirm the
 * knob moved with the setting rather than just the setting changing underneath it.
 *
 * Each switch is found by the caption beside it, never by ordinal, so re-ordering the column does not
 * silently re-point a test at a different control.
 */
class AtemSettingsTabTogglesTest {

    // ── Downstream keyer ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the downstream keyer switch sets useDownstreamKey`() = atemTab { get ->
        assertFalse(get().atemSettings.useDownstreamKey, "the key is upstream out of the box")

        atemSwitchFor(AtemLabel.DSK_SWITCH).performClick()
        waitForIdle()

        assertTrue(get().atemSettings.useDownstreamKey, "the switch must set useDownstreamKey")
        assertFalse(get().atemSettings.quickUpload, "and must not touch quick upload")
        assertFalse(get().atemSettings.goLiveKey, "nor the go-live key")
        atemSwitchFor(AtemLabel.DSK_SWITCH).assertIsOn()
    }

    @Test
    fun `the downstream keyer switch turns back off`() = atemTab(
        initial = atemSettings { copy(useDownstreamKey = true) },
    ) { get ->
        atemSwitchFor(AtemLabel.DSK_SWITCH).assertIsOn()

        atemSwitchFor(AtemLabel.DSK_SWITCH).performClick()
        waitForIdle()

        assertFalse(get().atemSettings.useDownstreamKey, "a second press must clear useDownstreamKey")
        atemSwitchFor(AtemLabel.DSK_SWITCH).assertIsOff()
    }

    // ── Quick upload ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the quick upload switch sets quickUpload`() = atemTab { get ->
        assertFalse(get().atemSettings.quickUpload, "the upload dialog is shown out of the box")

        atemSwitchFor(AtemLabel.QUICK_UPLOAD).performClick()
        waitForIdle()

        assertTrue(get().atemSettings.quickUpload, "the switch must set quickUpload")
        assertFalse(get().atemSettings.useDownstreamKey, "and must not touch the key type")
        assertFalse(get().atemSettings.goLiveKey, "nor the go-live key")
        atemSwitchFor(AtemLabel.QUICK_UPLOAD).assertIsOn()
    }

    @Test
    fun `the quick upload switch turns back off`() = atemTab(
        initial = atemSettings { copy(quickUpload = true) },
    ) { get ->
        atemSwitchFor(AtemLabel.QUICK_UPLOAD).assertIsOn()

        atemSwitchFor(AtemLabel.QUICK_UPLOAD).performClick()
        waitForIdle()

        assertFalse(get().atemSettings.quickUpload, "a second press must clear quickUpload")
        atemSwitchFor(AtemLabel.QUICK_UPLOAD).assertIsOff()
    }

    // ── Go Live drives the key ──────────────────────────────────────────────────────────────────

    @Test
    fun `the go live key switch sets goLiveKey`() = atemTab { get ->
        assertFalse(get().atemSettings.goLiveKey, "Go Live leaves the switcher alone out of the box")

        atemSwitchFor(AtemLabel.GO_LIVE_KEY).performClick()
        waitForIdle()

        assertTrue(get().atemSettings.goLiveKey, "the switch must set goLiveKey")
        assertFalse(get().atemSettings.useDownstreamKey, "and must not touch the key type")
        assertFalse(get().atemSettings.quickUpload, "nor quick upload")
        atemSwitchFor(AtemLabel.GO_LIVE_KEY).assertIsOn()
    }

    @Test
    fun `the go live key switch turns back off`() = atemTab(
        initial = atemSettings { copy(goLiveKey = true) },
    ) { get ->
        atemSwitchFor(AtemLabel.GO_LIVE_KEY).assertIsOn()

        atemSwitchFor(AtemLabel.GO_LIVE_KEY).performClick()
        waitForIdle()

        assertFalse(get().atemSettings.goLiveKey, "a second press must clear goLiveKey")
        atemSwitchFor(AtemLabel.GO_LIVE_KEY).assertIsOff()
    }

    // ── Together ────────────────────────────────────────────────────────────────────────────────

    /** Stored state, not the tab's own — a switch must show what the settings say when the tab opens. */
    @Test
    fun `each switch opens showing what the settings hold`() = atemTab(
        initial = atemSettings { copy(useDownstreamKey = true, quickUpload = false, goLiveKey = true) },
    ) { _ ->
        atemSwitchFor(AtemLabel.DSK_SWITCH).assertIsOn()
        atemSwitchFor(AtemLabel.QUICK_UPLOAD).assertIsOff()
        atemSwitchFor(AtemLabel.GO_LIVE_KEY).assertIsOn()
    }

    @Test
    fun `all three can be on at once`() = atemTab { get ->
        atemSwitchFor(AtemLabel.DSK_SWITCH).performClick()
        atemSwitchFor(AtemLabel.QUICK_UPLOAD).performClick()
        atemSwitchFor(AtemLabel.GO_LIVE_KEY).performClick()
        waitForIdle()

        val atem = get().atemSettings
        assertEquals(
            listOf(true, true, true),
            listOf(atem.useDownstreamKey, atem.quickUpload, atem.goLiveKey),
            "the three switches are independent — none unsets another",
        )
    }
}
