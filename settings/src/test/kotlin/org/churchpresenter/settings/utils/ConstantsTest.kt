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
    fun `the media upload default is a sane size`() {
        assertTrue(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB > 0)
        assertNotNull(Constants.MEDIA_SEEK_MS)
        assertTrue(Constants.MEDIA_SEEK_MS > 0)
    }
}
