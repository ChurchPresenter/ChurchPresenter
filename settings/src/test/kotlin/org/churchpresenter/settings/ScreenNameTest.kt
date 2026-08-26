package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Naming a monitor: what identifies it, what is stored, and what a label falls back to. */
class ScreenNameTest {

    private fun screen(x: Int = 1920, y: Int = 0, w: Int = 1280, h: Int = 720) =
        ScreenAssignment(
            targetDisplay = 1,
            targetBoundsX = x, targetBoundsY = y, targetBoundsW = w, targetBoundsH = h,
        )

    private val key = screenKey(1920, 0, 1280, 720)

    // ── What identifies a monitor ───────────────────────────────────────────────────────────────

    @Test
    fun `a monitor is keyed by its geometry`() {
        assertEquals("1280x720@1920,0", key)
    }

    @Test
    fun `two monitors of one size in different places are different monitors`() {
        assertEquals(false, screenKey(0, 0, 1920, 1080) == screenKey(1920, 0, 1920, 1080))
    }

    @Test
    fun `an unresolved slot has no key`() {
        // -1 auto, or a row whose target was never resolved: there is no monitor to name yet.
        assertEquals("", screenKey(Int.MIN_VALUE, Int.MIN_VALUE, 0, 0))
    }

    @Test
    fun `a zero-sized display has no key`() {
        assertEquals("", screenKey(0, 0, 0, 0))
    }

    @Test
    fun `a DeckLink output has no screen key`() {
        assertEquals("", screen().copy(targetType = "decklink").targetScreenKey)
    }

    @Test
    fun `an output driving a display is keyed by that display`() {
        assertEquals("1280x720@1920,0", screen().targetScreenKey)
    }

    // ── Storing the name ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a name is stored against the monitor`() {
        val proj = ProjectionSettings().withScreenName(key, "Foyer TV")

        assertEquals("Foyer TV", proj.screenName(key))
    }

    @Test
    fun `a name is stored exactly as typed`() {
        // Trimming on the way in deletes the space as it is pressed, so "Foyer TV" would stick at
        // "Foyer"; the trim belongs in screenName, where the name is read.
        val proj = ProjectionSettings().withScreenName(key, "Foyer ")

        assertEquals("Foyer ", proj.screenNames.getValue(key))
        assertEquals("Foyer", proj.screenName(key), "and is trimmed when read")
    }

    @Test
    fun `clearing a name drops the entry rather than storing an empty one`() {
        val proj = ProjectionSettings().withScreenName(key, "Foyer TV").withScreenName(key, "")

        assertEquals(emptyMap(), proj.screenNames)
    }

    @Test
    fun `a name of nothing but spaces is no name`() {
        assertEquals(emptyMap(), ProjectionSettings().withScreenName(key, "   ").screenNames)
    }

    @Test
    fun `a nameless key stores nothing`() {
        assertEquals(emptyMap(), ProjectionSettings().withScreenName("", "Foyer TV").screenNames)
    }

    @Test
    fun `naming one monitor leaves the others alone`() {
        val other = screenKey(3200, 0, 3840, 2160)
        val proj = ProjectionSettings().withScreenName(key, "Foyer TV").withScreenName(other, "Balcony")

        assertEquals("Foyer TV", proj.screenName(key))
        assertEquals("Balcony", proj.screenName(other))
    }

    // ── The label that comes out ────────────────────────────────────────────────────────────────

    @Test
    fun `an unnamed monitor falls back to its numbered label`() {
        assertEquals("Screen 1", ProjectionSettings().screenLabelOr(screen(), "Screen 1"))
    }

    @Test
    fun `a named monitor is labelled by its name`() {
        val proj = ProjectionSettings().withScreenName(key, "Foyer TV")

        assertEquals("Foyer TV", proj.screenLabelOr(screen(), "Screen 1"))
    }

    @Test
    fun `an output driving no display falls back however the monitors are named`() {
        val proj = ProjectionSettings().withScreenName(key, "Foyer TV")

        assertEquals("Screen 2", proj.screenLabelOr(ScreenAssignment(), "Screen 2"))
    }

    @Test
    fun `an output driving no display can still be named on the slot`() {
        val slotNamed = ScreenAssignment(screenName = "Rehearsal")

        assertEquals("Rehearsal", ProjectionSettings().screenLabelOr(slotNamed, "Dev Window"))
    }

    @Test
    fun `the monitor's name wins over a name left on the slot`() {
        val proj = ProjectionSettings().withScreenName(key, "Foyer TV")

        assertEquals("Foyer TV", proj.screenLabelOr(screen().copy(screenName = "Overflow"), "Screen 1"))
    }

    @Test
    fun `a slot name of nothing but spaces is no name`() {
        assertEquals(
            "Dev Window",
            ProjectionSettings().screenLabelOr(ScreenAssignment(screenName = "   "), "Dev Window"),
        )
    }

    @Test
    fun `the name follows the monitor when the outputs are re-ordered`() {
        // The point of keying by geometry: an output slot is a position, and unplugging the middle
        // monitor renumbers everything after it. The name must stay with the hardware.
        val proj = ProjectionSettings().withScreenName(key, "Foyer TV")
        val movedToSlotZero = screen()

        assertEquals("Foyer TV", proj.screenLabelOr(movedToSlotZero, "Screen 1"))
    }

    @Test
    fun `a name survives a settings round trip`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val settings = AppSettings(
            projectionSettings = ProjectionSettings().withScreenName(key, "Foyer TV"),
        )

        val restored = json.decodeFromString<AppSettings>(json.encodeToString(settings))

        assertEquals("Foyer TV", restored.projectionSettings.screenName(key))
    }
}
