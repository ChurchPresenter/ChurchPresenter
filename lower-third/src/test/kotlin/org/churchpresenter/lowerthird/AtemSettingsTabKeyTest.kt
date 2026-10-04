@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The key-sequencing row: which keyer Go Live and the Companion trigger drive, and the margins either
 * side of the animation.
 *
 * The row shows **one of two shapes**, decided by the downstream-key switch: upstream means an M/E
 * box and a keyer box, downstream means a single DSK box. Both shapes are driven here, including the
 * swap itself, because a box that is not composed cannot be typed into — a test that only ever saw
 * the default shape would never touch the DSK box at all.
 *
 * The keyer range is the one caption on the tab that depends on **another box**: how many upstream
 * keyers exist is a property of the selected M/E, so changing the M/E re-ranges the keyer beside it.
 */
class AtemSettingsTabKeyTest {

    // ── M/E ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the M-E box stores one less than it shows`() = atemTab { get ->
        assertEquals(0, get().atemSettings.keyMixEffect, "the first M/E is the default")

        type(atemFieldUnder(AtemLabel.ME), "2")

        assertEquals(1, get().atemSettings.keyMixEffect, "M/E 2 on screen is bus 1 on the wire")
        assertEquals(0, get().atemSettings.keyIndex, "the keyer must be untouched")
        atemFieldUnder(AtemLabel.ME).assertShows("2", "the M/E box")
    }

    @Test
    fun `an M-E below the first one is corrected on screen`() = atemTab(
        initial = atemSettings { copy(keyMixEffect = 2) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.ME), "0")

        assertEquals(0, get().atemSettings.keyMixEffect, "0 must clamp to the first M/E")
        atemFieldUnder(AtemLabel.ME).assertShows("1", "the box must show the bus it settled on")
    }

    @Test
    fun `an M-E that is not a number leaves the stored bus alone`() = atemTab(
        initial = atemSettings { copy(keyMixEffect = 1) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.ME), "program")

        assertEquals(1, get().atemSettings.keyMixEffect, "nonsense must not reach the stored bus")
        atemFieldUnder(AtemLabel.ME).assertShows("program", "the box, which does keep what was typed")
    }

    @Test
    fun `an M-E the switcher does not have is marked in error`() = atemTab(
        initial = atemSettings { copy(detectedMixEffects = 2) },
    ) { _ ->
        val ok = fieldBorderColour(atemFieldUnder(AtemLabel.ME))

        type(atemFieldUnder(AtemLabel.ME), "4")

        assertNotEquals(
            ok,
            fieldBorderColour(atemFieldUnder(AtemLabel.ME)),
            "a two-M/E switcher has no M/E 4",
        )
    }

    // ── Upstream keyer ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the keyer box stores one less than it shows`() = atemTab { get ->
        assertEquals(0, get().atemSettings.keyIndex, "the first keyer is the default")

        type(atemFieldUnder(AtemLabel.KEY), "3")

        assertEquals(2, get().atemSettings.keyIndex, "key 3 on screen is keyer 2 on the wire")
        assertEquals(0, get().atemSettings.keyMixEffect, "the M/E must be untouched")
        atemFieldUnder(AtemLabel.KEY).assertShows("3", "the keyer box")
    }

    @Test
    fun `a keyer below the first one is corrected on screen`() = atemTab(
        initial = atemSettings { copy(keyIndex = 3) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.KEY), "0")

        assertEquals(0, get().atemSettings.keyIndex, "0 must clamp to the first keyer")
        atemFieldUnder(AtemLabel.KEY).assertShows("1", "the box must show the keyer it settled on")
    }

    @Test
    fun `a keyer that is not a number leaves the stored keyer alone`() = atemTab(
        initial = atemSettings { copy(keyIndex = 2) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.KEY), "upstream")

        assertEquals(2, get().atemSettings.keyIndex, "nonsense must not reach the stored keyer")
        atemFieldUnder(AtemLabel.KEY).assertShows("upstream", "the box, which does keep what was typed")
    }

    /**
     * How many upstream keyers exist is per-M/E, so the keyer caption has to follow the M/E box. On
     * a switcher whose second M/E carries fewer keyers, switching bus narrows the range beside it.
     */
    @Test
    fun `the keyer range follows the selected M-E`() = atemTab(
        initial = atemSettings { copy(detectedMixEffects = 2, detectedKeyersPerMe = listOf(4, 1)) },
    ) { _ ->
        assertEquals("${AtemLabel.KEY} (1–4)", captionOf(AtemLabel.KEY), "M/E 1 carries four keyers")

        type(atemFieldUnder(AtemLabel.ME), "2")

        assertEquals("${AtemLabel.KEY} (1–1)", captionOf(AtemLabel.KEY), "M/E 2 carries one")
    }

    @Test
    fun `a keyer the selected M-E does not have is marked in error`() = atemTab(
        initial = atemSettings {
            copy(keyIndex = 2, detectedMixEffects = 2, detectedKeyersPerMe = listOf(4, 1))
        },
    ) { _ ->
        val ok = fieldBorderColour(atemFieldUnder(AtemLabel.KEY))

        type(atemFieldUnder(AtemLabel.ME), "2") // M/E 2 has one keyer; the selected key 3 is now invalid

        assertNotEquals(
            ok,
            fieldBorderColour(atemFieldUnder(AtemLabel.KEY)),
            "moving to an M/E with fewer keyers must flag the keyer left behind",
        )
    }

    @Test
    fun `the keyer caption is bare while the switcher is unknown`() = atemTab { _ ->
        assertEquals(AtemLabel.ME, captionOf(AtemLabel.ME), "no M/E count is known yet")
        assertEquals(AtemLabel.KEY, captionOf(AtemLabel.KEY), "no keyer count is known yet")
    }

    // ── Downstream keyer ────────────────────────────────────────────────────────────────────────

    /**
     * The row holds one shape or the other, never both — an upstream key and a downstream key are
     * different hardware, and offering both boxes would let a service be configured for a keyer it
     * is not driving.
     */
    @Test
    fun `the downstream switch swaps the M-E and keyer boxes for a DSK box`() = atemTab { _ ->
        assertTrue(hasFieldUnder(AtemLabel.ME), "upstream is the default shape")
        assertTrue(hasFieldUnder(AtemLabel.KEY), "upstream is the default shape")
        assertFalse(hasFieldUnder(AtemLabel.DSK), "and there is no DSK box while it is")

        atemSwitchFor(AtemLabel.DSK_SWITCH).performClick()
        waitForIdle()

        assertTrue(hasFieldUnder(AtemLabel.DSK), "switching to downstream must offer the DSK box")
        assertFalse(hasFieldUnder(AtemLabel.ME), "and take the M/E box away")
        assertFalse(hasFieldUnder(AtemLabel.KEY), "and the keyer box with it")
    }

    @Test
    fun `the DSK box stores one less than it shows`() = atemTab(
        initial = atemSettings { copy(useDownstreamKey = true) },
    ) { get ->
        assertEquals(0, get().atemSettings.dskIndex, "the first DSK is the default")

        type(atemFieldUnder(AtemLabel.DSK), "2")

        assertEquals(1, get().atemSettings.dskIndex, "DSK 2 on screen is keyer 1 on the wire")
        assertEquals(0, get().atemSettings.keyIndex, "the upstream keyer must be left as it was")
        atemFieldUnder(AtemLabel.DSK).assertShows("2", "the DSK box")
    }

    @Test
    fun `a DSK below the first one is corrected on screen`() = atemTab(
        initial = atemSettings { copy(useDownstreamKey = true, dskIndex = 2) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.DSK), "0")

        assertEquals(0, get().atemSettings.dskIndex, "0 must clamp to the first DSK")
        atemFieldUnder(AtemLabel.DSK).assertShows("1", "the box must show the keyer it settled on")
    }

    @Test
    fun `a DSK that is not a number leaves the stored keyer alone`() = atemTab(
        initial = atemSettings { copy(useDownstreamKey = true, dskIndex = 1) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.DSK), "downstream")

        assertEquals(1, get().atemSettings.dskIndex, "nonsense must not reach the stored keyer")
        atemFieldUnder(AtemLabel.DSK).assertShows("downstream", "the box, which does keep what was typed")
    }

    @Test
    fun `a detected downstream keyer count is printed in the DSK caption`() = atemTab(
        initial = atemSettings { copy(useDownstreamKey = true, detectedDownstreamKeyers = 2) },
    ) { _ ->
        assertEquals("${AtemLabel.DSK} (1–2)", captionOf(AtemLabel.DSK), "the detected DSK count")
    }

    @Test
    fun `the DSK caption is bare while the switcher is unknown`() = atemTab(
        initial = atemSettings { copy(useDownstreamKey = true) },
    ) { _ ->
        assertEquals(AtemLabel.DSK, captionOf(AtemLabel.DSK), "no DSK count is known yet")
    }

    @Test
    fun `a DSK the switcher does not have is marked in error`() = atemTab(
        initial = atemSettings { copy(useDownstreamKey = true, detectedDownstreamKeyers = 2) },
    ) { _ ->
        val ok = fieldBorderColour(atemFieldUnder(AtemLabel.DSK))

        type(atemFieldUnder(AtemLabel.DSK), "3")

        assertNotEquals(
            ok,
            fieldBorderColour(atemFieldUnder(AtemLabel.DSK)),
            "a two-DSK switcher has no DSK 3",
        )
    }

    // ── Roll margins ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the pre-roll box stores what is typed`() = atemTab { get ->
        assertEquals(300, get().atemSettings.keyPreRollMs, "300ms is the default")

        type(atemFieldUnder(AtemLabel.PRE_ROLL), "150")

        assertEquals(150, get().atemSettings.keyPreRollMs, "the typed delay must be stored")
        assertEquals(300, get().atemSettings.keyPostRollMs, "the post-roll must be untouched")
        atemFieldUnder(AtemLabel.PRE_ROLL).assertShows("150", "the pre-roll box")
    }

    /** A negative margin would order the key on air after the animation had already started. */
    @Test
    fun `a negative pre-roll is corrected to none`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.PRE_ROLL), "-200")

        assertEquals(0, get().atemSettings.keyPreRollMs, "a negative delay must clamp to none")
        atemFieldUnder(AtemLabel.PRE_ROLL).assertShows("0", "the box must show the delay it settled on")
    }

    @Test
    fun `a pre-roll that is not a number leaves the stored delay alone`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.PRE_ROLL), "instant")

        assertEquals(300, get().atemSettings.keyPreRollMs, "nonsense must not reach the stored delay")
        atemFieldUnder(AtemLabel.PRE_ROLL).assertShows("instant", "the box, which does keep what was typed")
    }

    @Test
    fun `the post-roll box stores what is typed`() = atemTab { get ->
        assertEquals(300, get().atemSettings.keyPostRollMs, "300ms is the default")

        type(atemFieldUnder(AtemLabel.POST_ROLL), "450")

        assertEquals(450, get().atemSettings.keyPostRollMs, "the typed delay must be stored")
        assertEquals(300, get().atemSettings.keyPreRollMs, "the pre-roll must be untouched")
        atemFieldUnder(AtemLabel.POST_ROLL).assertShows("450", "the post-roll box")
    }

    @Test
    fun `a negative post-roll is corrected to none`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.POST_ROLL), "-1")

        assertEquals(0, get().atemSettings.keyPostRollMs, "a negative delay must clamp to none")
        atemFieldUnder(AtemLabel.POST_ROLL).assertShows("0", "the box must show the delay it settled on")
    }

    @Test
    fun `a post-roll that is not a number leaves the stored delay alone`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.POST_ROLL), "later")

        assertEquals(300, get().atemSettings.keyPostRollMs, "nonsense must not reach the stored delay")
        atemFieldUnder(AtemLabel.POST_ROLL).assertShows("later", "the box, which does keep what was typed")
    }

    /** The roll margins apply to whichever keyer is driven, so they survive the shape swap. */
    @Test
    fun `the roll boxes stay on both sides of the downstream switch`() = atemTab(
        initial = atemSettings { copy(keyPreRollMs = 120, keyPostRollMs = 480) },
    ) { _ ->
        atemFieldUnder(AtemLabel.PRE_ROLL).assertShows("120", "the pre-roll box, upstream")
        atemFieldUnder(AtemLabel.POST_ROLL).assertShows("480", "the post-roll box, upstream")

        atemSwitchFor(AtemLabel.DSK_SWITCH).performClick()
        waitForIdle()

        atemFieldUnder(AtemLabel.PRE_ROLL).assertShows("120", "the pre-roll box, downstream")
        atemFieldUnder(AtemLabel.POST_ROLL).assertShows("480", "the post-roll box, downstream")
    }
}
