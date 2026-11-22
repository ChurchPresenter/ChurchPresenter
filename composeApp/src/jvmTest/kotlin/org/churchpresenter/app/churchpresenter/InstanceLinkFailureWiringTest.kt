@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.remote.instanceLinkPictureCacheDir
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.InstanceLinkCommandFailure
import org.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.server.InstanceLinkViewModel
import org.churchpresenter.server.updatePictures
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InstanceLinkFailureWiringTest {

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
    }

    @Test
    fun `a command that could not reach the primary is collected for the operator`() = runComposeUiTest {
        val viewModel = InstanceLinkViewModel()
        val failures = mutableListOf<InstanceLinkCommandFailure>()
        try {
            setContent { InstanceLinkFailureWiring(viewModel, failures) }
            waitForIdle()

            viewModel.sendClear()

            waitUntil(timeoutMillis = 5_000) { failures.isNotEmpty() }
            assertEquals("not_connected", failures.single().reason)
        } finally {
            viewModel.dispose()
        }
    }

    @Test
    fun `the primary's pictures changing clears the cached pictures`() = runComposeUiTest {
        val stale = File(instanceLinkPictureCacheDir, "folder_0.jpg").apply { writeText("old") }
        val server = CompanionServer(shutdownGraceMs = 0)
        val viewModel = InstanceLinkViewModel()
        try {
            server.updatePictures("folder", "Gallery", "/pics", emptyList())
            server.start(port = testPort(39_890))
            waitUntil(timeoutMillis = 10_000) { server.isRunning.value && server.serverUrl.value.isNotBlank() }
            setContent { InstanceLinkFailureWiring(viewModel, mutableListOf()) }
            waitForIdle()

            val port = server.serverUrl.value.substringAfterLast(':').toInt()
            viewModel.connect("127.0.0.1", port, apiKey = "", deviceId = "test-follower", reconnectDelayMs = 60_000)

            waitUntil(timeoutMillis = 10_000) { viewModel.connectionStatus.value == InstanceLinkStatus.CONNECTED }
            waitUntil(timeoutMillis = 10_000) { !stale.exists() }
            assertFalse(stale.exists())
        } finally {
            viewModel.dispose()
            server.stop()
            stale.delete()
        }
    }
}
