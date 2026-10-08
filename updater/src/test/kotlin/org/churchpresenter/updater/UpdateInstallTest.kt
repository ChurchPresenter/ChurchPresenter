package org.churchpresenter.updater

import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Running the downloaded installer and quitting, with both steps handed in: the app must quit only
 * once the installer has started, and a failure to start it is reported rather than thrown.
 */
class UpdateInstallTest {

    private val installer = File("ChurchPresenter-update-test.msi")

    @Test
    fun `the installer is started first and the app quits after it`() {
        val steps = mutableListOf<String>()

        val error = installAndQuit(
            installer,
            launch = { steps += "launch ${it.name}" },
            quit = { steps += "quit" },
        )

        assertNull(error)
        assertEquals(listOf("launch ChurchPresenter-update-test.msi", "quit"), steps)
    }

    @Test
    fun `an installer that cannot be started is reported and the app keeps running`() {
        var quit = false

        val error = installAndQuit(installer, launch = { throw IOException("no msiexec") }, quit = { quit = true })

        assertEquals(DownloadState.Error("no msiexec"), error)
        assertEquals(false, quit)
    }

    @Test
    fun `with the real quit step, a launch that fails never quits the app`() {
        // Were the default quit reached, it would end this test's JVM rather than fail the test.
        val error = installAndQuit(installer, launch = { throw IOException("no installer") })

        assertEquals(DownloadState.Error("no installer"), error)
    }

    @Test
    fun `a launch the security manager refuses is reported the same way`() {
        val error = installAndQuit(installer, launch = { throw SecurityException("denied") }, quit = {})

        assertEquals(DownloadState.Error("denied"), error)
    }

    @Test
    fun `a failure with no message still says what failed`() {
        assertEquals(
            DownloadState.Error("Failed to launch installer"),
            installAndQuit(installer, launch = { throw IOException() }, quit = {}),
        )
        assertEquals(
            DownloadState.Error("Failed to launch installer"),
            installAndQuit(installer, launch = { throw SecurityException() }, quit = {}),
        )
    }
}
