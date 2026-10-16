package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which typed ports the companion server can be started on. */
class ServerSettingsTest {

    @Test
    fun `a port in range is read as itself`() {
        assertEquals(8765, serverPortOrNull("8765"))
        assertEquals(1, serverPortOrNull("1"))
        assertEquals(65_535, serverPortOrNull("65535"))
    }

    @Test
    fun `a five-digit number past the last port is not a port`() {
        assertNull(serverPortOrNull("87659"))
        assertNull(serverPortOrNull("65536"))
    }

    @Test
    fun `blank and zero are not ports`() {
        assertNull(serverPortOrNull(""))
        assertNull(serverPortOrNull("0"))
    }
}
