package org.churchpresenter.settings.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fixed constants that other parts of the system are pinned to. The ports in particular must stay
 * distinct: two features binding the same localhost port fail at runtime, on a user's machine, with
 * an error that looks unrelated to either.
 */
class ConstantsTest {

    @Test
    fun `the fixed localhost ports are distinct and in the valid range`() {
        val ports = mapOf(
            "single instance" to Constants.SINGLE_INSTANCE_PORT,
            "planning center oauth" to Constants.PLANNING_CENTER_OAUTH_PORT,
            "companion server" to Constants.SERVER_DEFAULT_PORT,
        )
        for ((name, port) in ports) {
            assertTrue(port in 1024..65535, "$name port $port is outside the usable range")
        }
        assertEquals(ports.size, ports.values.toSet().size, "ports collide: $ports")
    }

    @Test
    fun `the Planning Center oauth port matches its registered redirect uri`() {
        // PCO requires an exact pre-registered redirect URI, so this value cannot drift without
        // also being changed in the developer app's settings.
        assertEquals(47850, Constants.PLANNING_CENTER_OAUTH_PORT)
    }

    @Test
    fun `timer modes are distinct identifiers`() {
        val modes = listOf(
            Constants.TIMER_MODE_DURATION,
            Constants.TIMER_MODE_CLOCK,
            Constants.TIMER_MODE_COUNT_UP,
            Constants.TIMER_MODE_CLOCK_DISPLAY,
        )
        assertEquals(modes.size, modes.toSet().size, "duplicate mode ids would alias two behaviours")
        assertTrue(modes.none { it.isBlank() })
    }

    @Test
    fun `an output's stored identity names its list and its place in it`() {
        // Two outputs of different kinds at the same index must not collide: the key is what a
        // per-output override is filed under, so a collision silently applies one screen's look
        // to another.
        assertEquals("screen:0", Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, 0))
        val keys = listOf(
            Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, 1),
            Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 1),
            Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 1),
        )
        assertEquals(keys.size, keys.toSet().size, "the kind has to survive into the key: $keys")
    }

    @Test
    fun `audio and video extensions are lowercase and never both`() {
        // A file is routed to one player or the other by these sets, so an extension in both is a
        // coin toss, and an uppercase entry never matches the lowercased suffix that is looked up.
        val audio = Constants.AUDIO_EXTENSIONS
        val video = Constants.VIDEO_EXTENSIONS
        assertTrue(audio.isNotEmpty() && video.isNotEmpty())
        assertTrue(audio.none { it.startsWith(".") } && video.none { it.startsWith(".") }, "suffixes, not globs")
        assertTrue(audio.all { it == it.lowercase() } && video.all { it == it.lowercase() })
        assertEquals(emptySet(), audio intersect video, "an extension claimed by both players")
    }

    @Test
    fun `the song section markers are the two shapes the parser reads back`() {
        // Written into the song file, so they are not translated and the chorus keys off braces.
        val markers = Constants.SONG_SECTION_MARKERS
        assertEquals(markers.size, markers.toSet().size, "a duplicate marker offers the same section twice")
        assertTrue(markers.all { it.startsWith("[") && it.endsWith("]") || it.startsWith("{") && it.endsWith("}") })
        assertTrue(markers.any { it.startsWith("{") }, "the chorus is the braced one")
    }

    @Test
    fun `the media upload default is a sane size`() {
        assertTrue(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB > 0)
        assertNotNull(Constants.MEDIA_SEEK_MS)
        assertTrue(Constants.MEDIA_SEEK_MS > 0)
    }
}
