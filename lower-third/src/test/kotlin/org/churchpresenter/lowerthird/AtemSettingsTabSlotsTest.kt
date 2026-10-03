@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The four media-pool slot boxes: the still and clip slots a lower third is uploaded to, and the two
 * still slots the Backgrounds settings upload to.
 *
 * All four behave identically and all four are tested separately, because the thing that would break
 * them is a copy-paste slip between the four `copy(...)` calls that back them — a test that drove one
 * box and trusted the rest would never see it. Each one therefore asserts that it writes **its own**
 * setting and that the other three are untouched.
 *
 * Two behaviours are shared and asserted per box:
 *
 *  * **1-based on screen, 0-based in the file**, matching ATEM Software Control. Typing 5 stores 4.
 *  * **A slot cannot go below the first one.** Typing 0 coerces to the first slot, and because the
 *    box is keyed on the stored value it re-renders as `1` — the operator sees the correction.
 *
 * The range shown in a caption and the error border are only possible once a Test Connection has
 * reported how many slots the switcher actually has; both states are driven here.
 */
class AtemSettingsTabSlotsTest {

    // ── Still slot ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the still slot box stores one less than it shows`() = atemTab { get ->
        assertEquals(0, get().atemSettings.defaultStillSlot, "the first slot is the default")

        type(atemFieldUnder(AtemLabel.STILL_SLOT), "5")

        assertEquals(4, get().atemSettings.defaultStillSlot, "slot 5 on screen is slot 4 on the wire")
        assertEquals(0, get().atemSettings.defaultClipSlot, "the clip slot must be untouched")
        assertEquals(1, get().atemSettings.backgroundSlot1, "background slot 1 must be untouched")
        assertEquals(2, get().atemSettings.backgroundSlot2, "background slot 2 must be untouched")
        atemFieldUnder(AtemLabel.STILL_SLOT).assertShows("5", "the still slot box")
    }

    @Test
    fun `a still slot below the first one is corrected on screen`() = atemTab(
        initial = atemSettings { copy(defaultStillSlot = 4) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.STILL_SLOT), "0")

        assertEquals(0, get().atemSettings.defaultStillSlot, "0 must clamp to the first slot, not go negative")
        atemFieldUnder(AtemLabel.STILL_SLOT).assertShows("1", "the box must show the slot it settled on")
    }

    @Test
    fun `a still slot that is not a number leaves the stored slot alone`() = atemTab(
        initial = atemSettings { copy(defaultStillSlot = 4) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.STILL_SLOT), "first")

        assertEquals(4, get().atemSettings.defaultStillSlot, "nonsense must not reach the stored slot")
        atemFieldUnder(AtemLabel.STILL_SLOT).assertShows("first", "the box, which does keep what was typed")
    }

    // ── Clip slot ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the clip slot box stores one less than it shows`() = atemTab { get ->
        assertEquals(0, get().atemSettings.defaultClipSlot, "the first slot is the default")

        type(atemFieldUnder(AtemLabel.CLIP_SLOT), "2")

        assertEquals(1, get().atemSettings.defaultClipSlot, "slot 2 on screen is slot 1 on the wire")
        assertEquals(0, get().atemSettings.defaultStillSlot, "the still slot must be untouched")
        atemFieldUnder(AtemLabel.CLIP_SLOT).assertShows("2", "the clip slot box")
    }

    @Test
    fun `a clip slot below the first one is corrected on screen`() = atemTab(
        initial = atemSettings { copy(defaultClipSlot = 3) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.CLIP_SLOT), "0")

        assertEquals(0, get().atemSettings.defaultClipSlot, "0 must clamp to the first slot")
        atemFieldUnder(AtemLabel.CLIP_SLOT).assertShows("1", "the box must show the slot it settled on")
    }

    @Test
    fun `a clip slot that is not a number leaves the stored slot alone`() = atemTab(
        initial = atemSettings { copy(defaultClipSlot = 3) },
    ) { get ->
        type(atemFieldUnder(AtemLabel.CLIP_SLOT), "last")

        assertEquals(3, get().atemSettings.defaultClipSlot, "nonsense must not reach the stored slot")
        atemFieldUnder(AtemLabel.CLIP_SLOT).assertShows("last", "the box, which does keep what was typed")
    }

    // ── Background slots ────────────────────────────────────────────────────────────────────────

    /**
     * The background slots live in their own card precisely so a background upload can never land on
     * the lower-third still; that separation is only real if the box writes its own field, so the
     * lower-third slot is asserted unchanged.
     */
    @Test
    fun `the first background slot box stores one less than it shows`() = atemTab { get ->
        assertEquals(1, get().atemSettings.backgroundSlot1, "the second slot is the shipped default")

        type(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1), "7")

        assertEquals(6, get().atemSettings.backgroundSlot1, "slot 7 on screen is slot 6 on the wire")
        assertEquals(2, get().atemSettings.backgroundSlot2, "the second background slot must be untouched")
        assertEquals(0, get().atemSettings.defaultStillSlot, "the lower-third still slot must be untouched")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1).assertShows("7", "the first background slot box")
    }

    @Test
    fun `the second background slot box stores one less than it shows`() = atemTab { get ->
        assertEquals(2, get().atemSettings.backgroundSlot2, "the third slot is the shipped default")

        type(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2), "9")

        assertEquals(8, get().atemSettings.backgroundSlot2, "slot 9 on screen is slot 8 on the wire")
        assertEquals(1, get().atemSettings.backgroundSlot1, "the first background slot must be untouched")
        assertEquals(0, get().atemSettings.defaultStillSlot, "the lower-third still slot must be untouched")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2).assertShows("9", "the second background slot box")
    }

    @Test
    fun `a background slot below the first one is corrected on screen`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1), "0")

        assertEquals(0, get().atemSettings.backgroundSlot1, "0 must clamp to the first slot")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1).assertShows("1", "the box must show the slot it settled on")
    }

    @Test
    fun `a background slot that is not a number leaves the stored slot alone`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2), "the other one")

        assertEquals(2, get().atemSettings.backgroundSlot2, "nonsense must not reach the stored slot")
        atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2)
            .assertShows("the other one", "the box, which does keep what was typed")
    }

    // ── What a Test Connection teaches the slot boxes ───────────────────────────────────────────

    @Test
    fun `a slot caption is bare until the switcher reports how many slots it has`() = atemTab { _ ->
        assertEquals(AtemLabel.STILL_SLOT, captionOf(AtemLabel.STILL_SLOT), "no range is known yet")
        assertEquals(AtemLabel.CLIP_SLOT, captionOf(AtemLabel.CLIP_SLOT), "no range is known yet")
        assertEquals(AtemLabel.BACKGROUND_SLOT_1, captionOf(AtemLabel.BACKGROUND_SLOT_1), "no range is known yet")
        assertEquals(AtemLabel.BACKGROUND_SLOT_2, captionOf(AtemLabel.BACKGROUND_SLOT_2), "no range is known yet")
    }

    @Test
    fun `a detected slot count is printed in the caption`() = atemTab(
        initial = atemSettings { copy(detectedStillSlots = 20, detectedClipSlots = 2) },
    ) { _ ->
        assertEquals("${AtemLabel.STILL_SLOT} (1–20)", captionOf(AtemLabel.STILL_SLOT), "the still store size")
        assertEquals("${AtemLabel.CLIP_SLOT} (1–2)", captionOf(AtemLabel.CLIP_SLOT), "the clip bank count")
        assertEquals(
            "${AtemLabel.BACKGROUND_SLOT_1} (1–20)",
            captionOf(AtemLabel.BACKGROUND_SLOT_1),
            "background uploads go to the still store, so they share its range",
        )
        assertEquals("${AtemLabel.BACKGROUND_SLOT_2} (1–20)", captionOf(AtemLabel.BACKGROUND_SLOT_2))
    }

    /**
     * The only thing a `SettingsTextField` changes when it is in error is its border colour, so that
     * is what is asserted — see `fieldBorderColour`. Typing a slot the switcher does not have has to
     * show, or the upload fails later with nothing on screen having warned about it.
     */
    @Test
    fun `a slot the switcher does not have is marked in error`() = atemTab(
        initial = atemSettings { copy(defaultStillSlot = 4, detectedStillSlots = 20) },
    ) { _ ->
        val ok = focusedFieldBorderColour(atemFieldUnder(AtemLabel.STILL_SLOT))

        type(atemFieldUnder(AtemLabel.STILL_SLOT), "99")
        val bad = fieldBorderColour(atemFieldUnder(AtemLabel.STILL_SLOT))
        assertNotEquals(ok, bad, "slot 99 on a 20-slot switcher must recolour the border")

        type(atemFieldUnder(AtemLabel.STILL_SLOT), "3")
        assertEquals(ok, fieldBorderColour(atemFieldUnder(AtemLabel.STILL_SLOT)), "a slot in range must clear it")
    }

    @Test
    fun `a slot that is not a number is marked in error too`() = atemTab(
        initial = atemSettings { copy(defaultStillSlot = 4, detectedStillSlots = 20) },
    ) { _ ->
        val ok = focusedFieldBorderColour(atemFieldUnder(AtemLabel.STILL_SLOT))

        type(atemFieldUnder(AtemLabel.STILL_SLOT), "first")

        assertNotEquals(
            ok,
            fieldBorderColour(atemFieldUnder(AtemLabel.STILL_SLOT)),
            "a slot that cannot be parsed is no more uploadable than one out of range",
        )
    }

    /** With nothing detected there is no range to judge against, so nothing is flagged. */
    @Test
    fun `no slot is flagged before a Test Connection has run`() = atemTab { _ ->
        val ok = focusedFieldBorderColour(atemFieldUnder(AtemLabel.STILL_SLOT))

        type(atemFieldUnder(AtemLabel.STILL_SLOT), "99")

        assertEquals(
            ok,
            fieldBorderColour(atemFieldUnder(AtemLabel.STILL_SLOT)),
            "an unknown switcher cannot be said to lack slot 99",
        )
    }

    @Test
    fun `a background slot outside the still store is marked in error`() = atemTab(
        initial = atemSettings { copy(backgroundSlot1 = 1, detectedStillSlots = 20) },
    ) { _ ->
        // Slot 2 is never touched, so its baseline is a resting one; slot 1's is taken focused
        // because it is read again after being typed into.
        val restingNeighbour = fieldBorderColour(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2))
        val ok = focusedFieldBorderColour(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1))

        type(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1), "40")

        assertNotEquals(
            ok,
            fieldBorderColour(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_1)),
            "a background upload to a slot the still store does not have must be flagged",
        )
        assertEquals(
            restingNeighbour,
            fieldBorderColour(atemFieldUnder(AtemLabel.BACKGROUND_SLOT_2)),
            "and its neighbour, still in range, must not be",
        )
    }

    @Test
    fun `a clip slot outside the clip banks is marked in error`() = atemTab(
        initial = atemSettings { copy(defaultClipSlot = 0, detectedClipSlots = 2) },
    ) { _ ->
        val ok = focusedFieldBorderColour(atemFieldUnder(AtemLabel.CLIP_SLOT))

        type(atemFieldUnder(AtemLabel.CLIP_SLOT), "5")

        assertNotEquals(
            ok,
            fieldBorderColour(atemFieldUnder(AtemLabel.CLIP_SLOT)),
            "a two-bank switcher has no clip slot 5",
        )
    }
}
