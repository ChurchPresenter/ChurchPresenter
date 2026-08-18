package org.churchpresenter.app.churchpresenter

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the port banding the parallel forks depend on.
 *
 * Worth a test of its own because the failure it prevents is invisible in a normal run: a wrong
 * offset only collides when two forks bind at the same moment, so it would surface as an occasional
 * `BindException` in whichever server suite happened to be unlucky.
 */
class TestPortsTest {

    private val original: String? = System.getProperty(PerForkTestHome.PORT_OFFSET_PROPERTY)

    @AfterTest
    fun restore() {
        if (original == null) {
            System.clearProperty(PerForkTestHome.PORT_OFFSET_PROPERTY)
        } else {
            System.setProperty(PerForkTestHome.PORT_OFFSET_PROPERTY, original)
        }
    }

    @Test
    fun `the base port is used unchanged when no fork offset is set`() {
        System.clearProperty(PerForkTestHome.PORT_OFFSET_PROPERTY)

        assertEquals(39_780, testPort(39_780), "an IDE run must bind the port written in the source")
    }

    @Test
    fun `a fork shifts every port by its own band`() {
        System.setProperty(PerForkTestHome.PORT_OFFSET_PROPERTY, "3000")

        assertEquals(42_780, testPort(39_780))
        assertEquals(42_517, testPort(39_517))
    }

    @Test
    fun `two suites keep their distance inside a fork`() {
        System.setProperty(PerForkTestHome.PORT_OFFSET_PROPERTY, "2000")

        // The whole range moves together, so ports that differed before still differ after -- which
        // is what lets suites keep claiming distinct numbers without knowing about forks at all.
        assertEquals(testPort(39_800) - testPort(39_780), 20)
    }

    @Test
    fun `every slot stays inside the valid port range`() {
        // PerForkTestHome hands out slot * 1000 for slots 0..7, on top of bases in the 39_5xx-39_8xx
        // band. The top of that must stay a bindable port.
        val highestBase = 39_895
        System.setProperty(PerForkTestHome.PORT_OFFSET_PROPERTY, (7 * 1_000).toString())

        assertTrue(testPort(highestBase) < 65_536, "slot 7 must not push a port past the 16-bit limit")
    }

    @Test
    fun `a malformed offset falls back to the base rather than throwing`() {
        System.setProperty(PerForkTestHome.PORT_OFFSET_PROPERTY, "not-a-number")

        assertEquals(39_780, testPort(39_780))
    }
}
