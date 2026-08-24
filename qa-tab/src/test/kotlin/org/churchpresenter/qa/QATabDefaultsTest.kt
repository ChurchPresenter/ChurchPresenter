@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.companionserver.TunnelStatus
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * The tab composed with nothing but the three things it cannot do without.
 *
 * `QATab` takes ten defaulted parameters — settings, the tunnel, the display URL, the device-name
 * lookup and the two file choosers — and `MainDesktop` passes every one of them, so nothing else
 * ever exercises the defaults. They are not decoration: the file choosers default to "cancelled",
 * `resolveDeviceName` to "no name", and the settings to `AppSettings()`, and a tab handed only a
 * manager, an output and a URL has to draw.
 */
class QATabDefaultsTest {

    private fun withTempHome(block: () -> Unit) {
        val realHome = System.getProperty("user.home")
        val tempHome: File = Files.createTempDirectory("cp-qa-defaults").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        try {
            block()
        } finally {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
    }

    @Test
    fun `the tab draws given only a manager, an output and a server URL`() = withTempHome {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    QATab(
                        qaManager = QAManager(),
                        output = FakeQaOutput(),
                        serverUrl = "http://192.0.2.1:8080",
                    )
                }
            }
            waitForIdle()

            assertTrue(showsContainingText(QALabel.NEW_SESSION), renderedText().toString())
        }
    }

    @Test
    fun `with no server running the tab says so rather than showing a join code`() = withTempHome {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    QATab(
                        qaManager = QAManager(),
                        output = FakeQaOutput(),
                        serverUrl = "",
                    )
                }
            }
            waitForIdle()

            assertTrue(
                showsContainingText("Server"),
                "an unreachable server must be stated, not left to a blank QR: ${renderedText()}",
            )
        }
    }

    @Test
    fun `every parameter can be given explicitly`() = withTempHome {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    QATab(
                        modifier = Modifier.fillMaxSize(),
                        qaManager = QAManager(),
                        output = FakeQaOutput(),
                        serverUrl = "http://192.0.2.1:8080",
                        appSettings = AppSettings(qaSettings = QASettings(fontSize = 64)),
                        onSettingsChange = {},
                        tunnelStatus = TunnelStatus.Connected("https://abc-def.trycloudflare.com"),
                        tunnelUrl = "https://abc-def.trycloudflare.com",
                        onStartTunnel = {},
                        onStopTunnel = {},
                        qaDisplayUrl = "https://example.church/qa",
                        onQaDisplayUrlChanged = {},
                        resolveDeviceName = { "Back row iPad" },
                        chooseExportFile = { _, _ -> null },
                        chooseImportFile = { null },
                    )
                }
            }
            waitForIdle()

            // The counterpart to the two tests above: with every parameter supplied, none of the
            // defaults runs. Between them the two call shapes cover both sides of each.
            assertTrue(showsContainingText(QALabel.NEW_SESSION), renderedText().toString())
        }
    }
}
