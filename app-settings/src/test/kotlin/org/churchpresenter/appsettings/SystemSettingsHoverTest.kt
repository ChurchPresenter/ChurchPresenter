@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.junit.jupiter.api.Assumptions
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * What the System page says on hover -- the tab-label buttons' hints -- and what it says when the
 * Bible folder cannot take a download.
 */
class SystemSettingsHoverTest {

    @Test
    fun `hovering a tab-label button shows what it does`() = runComposeUiTest {
        setContent { MaterialTheme { SystemSettingsTab(settings = AppSettings()) } }
        waitForIdle()
        val button = onNode(hasText("Text only") and hasClickAction()).performScrollTo()
        button.performMouseInput { enter(center); moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_DELAY_MS)
        waitForIdle()
        onNodeWithText("Tab labels: text, icons, or both. Click to change.").assertExists()
    }

    @Test
    fun `a Bible folder that cannot be written says so instead of opening the catalogue`() {
        val locked = Files.createTempDirectory("bibles-locked").toFile()
        try {
            locked.setWritable(false)
            val stillWritable = runCatching { File(locked, "probe").createNewFile() }.getOrDefault(false)
            Assumptions.assumeFalse(stillWritable, "this OS ignores the permission")
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        SystemSettingsTab(
                            settings = AppSettings(
                                bibleSettings = BibleSettings(storageDirectory = locked.absolutePath),
                            ),
                        )
                    }
                }
                waitForIdle()
                onAllNodesWithText("Download Bibles…")[0].performScrollTo().performClick()
                waitForIdle()
                onAllNodesWithText(NOT_WRITABLE, substring = true)[0].assertExists()
            }
        } finally {
            locked.setWritable(true)
            locked.deleteRecursively()
        }
    }

    @Test
    fun `with nothing passed the page draws on the defaults and its switches are safe to press`() = runComposeUiTest {
        setContent { MaterialTheme { SystemSettingsTab() } }
        waitForIdle()
        onNodeWithText("Hide the mouse on output screens").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Hide the mouse on output screens").assertExists()
    }

    @Test
    fun `hovering a set folder's status dot says whether it can be written`() {
        val bibles = Files.createTempDirectory("bibles-hover").toFile()
        try {
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        SystemSettingsTab(
                            settings = AppSettings(
                                bibleSettings = BibleSettings(storageDirectory = bibles.absolutePath),
                            ),
                        )
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag(STORAGE_STATUS_DOT_TAG).fetchSemanticsNodes().isNotEmpty()
                }
                val dot = onAllNodesWithTag(STORAGE_STATUS_DOT_TAG)[0].performScrollTo()
                dot.performMouseInput { enter(center); moveTo(center) }
                mainClock.advanceTimeBy(TOOLTIP_DELAY_MS)
                waitForIdle()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("Directory is writable").fetchSemanticsNodes().isNotEmpty()
                }
            }
        } finally {
            bibles.deleteRecursively()
        }
    }

    private companion object {
        const val TOOLTIP_DELAY_MS = 1_000L
        const val NOT_WRITABLE = "can't be written to, so Bibles can't be downloaded"
    }
}
