package org.churchpresenter.canvas

import org.junit.Assume.assumeFalse
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserProcessesTest {

    @BeforeTest
    fun unixOnly() {
        // `which` and `sh` stand in for the browser lookup; Windows asks `where` the same way.
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
    }

    @Test
    fun `a program on the path is found where it lives`() {
        val found = BrowserProcesses.browserOnPath("which", "sh")

        assertTrue(found != null && File(found).exists())
    }

    @Test
    fun `a program that is not installed is not found`() {
        assertNull(BrowserProcesses.browserOnPath("which", "no-such-browser-anywhere"))
    }

    @Test
    fun `a lookup command that does not exist finds nothing rather than failing`() {
        assertNull(BrowserProcesses.browserOnPath("no-such-which", "sh"))
    }

    @Test
    fun `an answer naming a file that is not there is not trusted`() {
        assertNull(BrowserProcesses.browserOnPath("echo", "/no/such/browser"))
    }
}
