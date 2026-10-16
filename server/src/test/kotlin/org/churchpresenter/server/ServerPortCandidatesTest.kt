package org.churchpresenter.server

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ports the companion server tries, which must all be ones a `ServerSocket` accepts. */
class ServerPortCandidatesTest {

    @Test
    fun `a valid port is tried first, then the ones above it`() {
        assertEquals(8765..8804, serverPortCandidates(8765))
    }

    @Test
    fun `a port past the last one starts from the default instead`() {
        assertEquals(Constants.SERVER_DEFAULT_PORT, serverPortCandidates(87_659).first)
        assertEquals(Constants.SERVER_DEFAULT_PORT, serverPortCandidates(0).first)
    }

    @Test
    fun `the scan stops at the last port`() {
        assertEquals(65_530..65_535, serverPortCandidates(65_530))
    }
}
