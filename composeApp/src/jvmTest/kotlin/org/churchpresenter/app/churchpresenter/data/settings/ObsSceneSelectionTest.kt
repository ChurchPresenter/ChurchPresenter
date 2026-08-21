package org.churchpresenter.app.churchpresenter.data.settings

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.OBSSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which OBS scene the stream cuts to when the live content type changes.
 *
 * This runs on every content change during a service and drives a real scene switch on the broadcast.
 * A wrong answer is visible to everyone watching, so the cases below are about *not* switching as
 * much as about switching.
 *
 * The blank handling is the load-bearing part. `OBSSettingsTab` writes each mapping straight from a
 * text field, so someone who types a scene name and then clears it leaves `""` in the map rather than
 * removing the key. Taking that at face value would ask OBS to switch to a scene named `""` the next
 * time that content goes live.
 */
class ObsSceneSelectionTest {

    private fun settings(
        enabled: Boolean = true,
        default: String = "",
        mappings: Map<String, String> = emptyMap(),
    ) = OBSSettings(enabled = enabled, defaultScene = default, sceneMappings = mappings)

    // ── Switching ───────────────────────────────────────────────────────────────

    @Test
    fun `a mapped content type switches to its own scene`() {
        val obs = settings(mappings = mapOf(Presenting.LYRICS.name to "Worship"))

        assertEquals("Worship", obsSceneFor(Presenting.LYRICS, obs))
    }

    @Test
    fun `a mapping wins over the default`() {
        val obs = settings(default = "Wide", mappings = mapOf(Presenting.BIBLE.name to "Scripture"))

        assertEquals("Scripture", obsSceneFor(Presenting.BIBLE, obs))
    }

    @Test
    fun `an unmapped content type falls back to the default`() {
        val obs = settings(default = "Wide", mappings = mapOf(Presenting.LYRICS.name to "Worship"))

        assertEquals("Wide", obsSceneFor(Presenting.BIBLE, obs))
    }

    @Test
    fun `each content type gets its own scene`() {
        val obs = settings(
            mappings = mapOf(
                Presenting.LYRICS.name to "Worship",
                Presenting.BIBLE.name to "Scripture",
                Presenting.NONE.name to "Holding",
            ),
        )

        assertEquals("Worship", obsSceneFor(Presenting.LYRICS, obs))
        assertEquals("Scripture", obsSceneFor(Presenting.BIBLE, obs))
        assertEquals("Holding", obsSceneFor(Presenting.NONE, obs))
    }

    // ── Not switching ───────────────────────────────────────────────────────────

    @Test
    fun `integration disabled never switches, even with everything configured`() {
        val obs = settings(enabled = false, default = "Wide", mappings = mapOf(Presenting.LYRICS.name to "Worship"))

        assertNull(obsSceneFor(Presenting.LYRICS, obs))
        assertNull(obsSceneFor(Presenting.BIBLE, obs))
    }

    @Test
    fun `nothing configured means no switch rather than an empty scene name`() {
        assertNull(obsSceneFor(Presenting.LYRICS, settings()))
    }

    // ── Blank is unset, not a scene name ────────────────────────────────────────

    @Test
    fun `a cleared mapping falls back to the default instead of switching to nothing`() {
        val obs = settings(default = "Wide", mappings = mapOf(Presenting.LYRICS.name to ""))

        assertEquals(
            "Wide", obsSceneFor(Presenting.LYRICS, obs),
            "clearing the text field leaves \"\" in the map; it must not reach OBS as a scene name",
        )
    }

    @Test
    fun `a whitespace-only mapping is also treated as unset`() {
        val obs = settings(default = "Wide", mappings = mapOf(Presenting.LYRICS.name to "   "))

        assertEquals("Wide", obsSceneFor(Presenting.LYRICS, obs))
    }

    @Test
    fun `a blank default means no default, not a scene with no name`() {
        val obs = settings(default = "  ", mappings = mapOf(Presenting.LYRICS.name to "Worship"))

        assertNull(obsSceneFor(Presenting.BIBLE, obs))
        assertEquals("Worship", obsSceneFor(Presenting.LYRICS, obs))
    }

    @Test
    fun `a cleared mapping with no default switches nothing at all`() {
        val obs = settings(mappings = mapOf(Presenting.LYRICS.name to ""))

        assertNull(obsSceneFor(Presenting.LYRICS, obs))
    }

    // ── Keys are enum names, which is what the settings tab writes ──────────────

    @Test
    fun `a mapping keyed by something that is not a content type is ignored`() {
        val obs = settings(default = "Wide", mappings = mapOf("Lyrics" to "Worship"))

        assertEquals(
            "Wide", obsSceneFor(Presenting.LYRICS, obs),
            "keys are enum names — a stale or hand-edited key must not half-match",
        )
    }
}
