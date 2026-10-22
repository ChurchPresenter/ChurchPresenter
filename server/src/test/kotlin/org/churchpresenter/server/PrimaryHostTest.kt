package org.churchpresenter.server

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What [normalizePrimaryHost] makes of what operators type into Instance Link's Host field.
 *
 * The first rows are the field's own reports (CHURCH-PRESENTER-DESKTOP-DM): an address copied from
 * the primary's Server page, scheme and port included, that the client passed on whole and ktor
 * could not resolve.
 */
class PrimaryHostTest {

    @Test
    fun `each way of writing a primary's address comes down to its host and port`() {
        val cases = listOf(
            "192.168.0.112:8765" to PrimaryHost("192.168.0.112", 8765),
            "http://192.168.2.119:8765" to PrimaryHost("192.168.2.119", 8765),
            "10.162.97.239:8768" to PrimaryHost("10.162.97.239", 8768),
            "192.168.1.10" to PrimaryHost("192.168.1.10", null),
            "  studio.local  " to PrimaryHost("studio.local", null),
            "https://studio.local/" to PrimaryHost("studio.local", null),
            "HTTPS://Studio.local:443/remote?x=1" to PrimaryHost("Studio.local", 443),
            "ws://10.0.0.2:8765/ws" to PrimaryHost("10.0.0.2", 8765),
            "wss://10.0.0.2:8443/ws#frag" to PrimaryHost("10.0.0.2", 8443),
            "studio.local:" to PrimaryHost("studio.local", null),
            "[::1]:8765" to PrimaryHost("[::1]", 8765),
            "http://[fe80::1]:8765/ws" to PrimaryHost("[fe80::1]", 8765),
            "[::1]" to PrimaryHost("[::1]", null),
            "::1" to PrimaryHost("[::1]", null),
            "fe80::1:2" to PrimaryHost("[fe80::1:2]", null),
            "" to PrimaryHost("", null),
        )
        for ((raw, expected) in cases) {
            assertEquals(expected, normalizePrimaryHost(raw), "\"$raw\"")
        }
    }

    @Test
    fun `a port that is not a port is dropped, never guessed`() {
        assertEquals(PrimaryHost("studio.local", null), normalizePrimaryHost("studio.local:99999"))
        assertEquals(PrimaryHost("studio.local", null), normalizePrimaryHost("studio.local:0"))
        assertEquals(
            PrimaryHost("studio.local:abc", null),
            normalizePrimaryHost("studio.local:abc"),
            "text that is not a port is left for the connect to fail on, not silently cut",
        )
        assertEquals(PrimaryHost("[::1", null), normalizePrimaryHost("[::1"))
    }
}
