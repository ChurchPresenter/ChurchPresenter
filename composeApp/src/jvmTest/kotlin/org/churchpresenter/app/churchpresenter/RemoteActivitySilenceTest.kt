package org.churchpresenter.app.churchpresenter

import org.churchpresenter.server.DevicePermission
import org.churchpresenter.server.RemoteActivityNotification
import org.churchpresenter.server.RemoteEventType
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A trusted device the operator has silenced -- its *Show activity notifications* switched off --
 * does what it does without a toast; every other device still raises one. The toasts are what a
 * church asked to be rid of mid-service, for its own booth tablet, and only for that.
 */
class RemoteActivitySilenceTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-remote-silence").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun presented(clientId: String) =
        RemoteActivityNotification(type = RemoteEventType.PRESENT, title = "John 3:16", clientId = clientId)

    @Test
    fun `a silenced device raises no toast, and every other device still does`() {
        val window = MainWindowState(calendarSyncInitiallyOn = false)
        listOf("booth-ipad", "pastor-phone").forEach(window.remoteClientManager::allowPermanently)
        window.remoteClientManager.setPermission("booth-ipad", DevicePermission.SHOW_TOASTS, false)

        listOf("booth-ipad", "pastor-phone", "stranger", "").forEach { window.notifyRemoteActivity(presented(it)) }

        assertEquals(
            listOf("pastor-phone", "stranger", ""),
            window.remoteActivityNotifications.map { it.clientId },
        )
    }
}
