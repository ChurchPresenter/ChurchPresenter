@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.profiles.LocalSettingsDevMode
import org.churchpresenter.settings.AppSettings
import java.nio.file.Files
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import javax.swing.JOptionPane
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.settings.BibleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The General card's switches, dev mode's preview switch and the calendar's Use Default, each written back. */
class SystemSettingsTogglesTest {

    private fun ComposeUiTest.tab(initial: AppSettings, devMode: Boolean = false): () -> AppSettings {
        var settings by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSettingsDevMode provides devMode) {
                    SystemSettingsTab(settings = settings, onSettingsChange = { settings = it(settings) })
                }
            }
        }
        waitForIdle()
        return { settings }
    }

    private fun ComposeUiTest.press(label: String) {
        onNodeWithText(label).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `each output switch on the General card writes its own setting`() = runComposeUiTest {
        val read = tab(AppSettings())
        val before = read().projectionSettings
        press("Start with output screens hidden")
        press("Hide the mouse on output screens")
        press("Clear the screen when a lower third or announcement ends")
        val after = read().projectionSettings
        assertEquals(!before.startOutputsHidden, after.startOutputsHidden)
        assertEquals(!before.hideCursorOnOutputs, after.hideCursorOnOutputs)
        assertEquals(!before.overlayEndClearsDisplay, after.overlayEndClearsDisplay)
    }

    @Test
    fun `in dev mode the preview switch turns preview mode on`() = runComposeUiTest {
        val read = tab(AppSettings(), devMode = true)
        press("Preview mode")
        assertTrue(read().projectionSettings.previewModeEnabled)
    }

    @Test
    fun `Use Default puts the calendar back in the app data folder`() {
        val shared = Files.createTempDirectory("shared-calendar").toFile()
        try {
            runComposeUiTest {
                val read = tab(AppSettings(calendarStorageDirectory = shared.absolutePath))
                press("Use Default")
                assertEquals("", read().calendarStorageDirectory)
            }
        } finally {
            shared.deleteRecursively()
        }
    }

    @Test
    fun `a sent test event says whether it went, and with reporting never started it did not`() {
        val told = mutableListOf<Any?>()
        mockkStatic(JOptionPane::class)
        try {
            every { JOptionPane.showMessageDialog(any(), any(), any(), any()) } answers { told += secondArg<Any?>() }
            runComposeUiTest {
                tab(AppSettings(analyticsReportingEnabled = true), devMode = true)
                onNode(hasText("Send test event") and hasClickAction()).performScrollTo().performClick()
                waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }
                assertEquals(
                    listOf<Any?>("Could not send test event. Crash reporting is disabled or no DSN is configured."),
                    told.toList(),
                )
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `a folder's own Set All copies its path over every content folder`() {
        val bibles = Files.createTempDirectory("set-all-bibles").toFile()
        try {
            runComposeUiTest {
                val read = tab(AppSettings(bibleSettings = BibleSettings(storageDirectory = bibles.absolutePath)))
                onAllNodesWithText("Set All")[0].performScrollTo().performClick()
                waitForIdle()
                val s = read()
                assertEquals(bibles.absolutePath, s.songSettings.storageDirectory)
                assertEquals(bibles.absolutePath, s.mediaStorageDirectory)
            }
        } finally {
            bibles.deleteRecursively()
        }
    }
}
