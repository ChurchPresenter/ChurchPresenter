package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.ui.ScanningRow
import kotlin.test.Test

/**
 * The folder-scan progress row in the directory settings.
 *
 * Why this exists: both the Bible and Songs folder scans run on `Dispatchers.IO` and take seconds
 * against a network share, and until this row landed the UI rendered "No files detected" in red for
 * the whole wait. Someone who had just picked the correct folder was told it was empty.
 *
 * **Not covered:** that `SystemSettingsTab` actually shows this row *while* a scan is in flight.
 * The tab constructs its own `FileManager`, so a test cannot hold a scan open, and asserting on a
 * state that resolves in microseconds would mean racing it — the flake shape `AGENT.md` rules out.
 * Making it reachable needs a production seam (an injectable `FileManager`), which is a change to
 * flag rather than to smuggle into a UI fix. What is covered here is the row itself, and the
 * scanned-to-completion outcomes are covered by `SystemSettingsTabTest`.
 */
@OptIn(ExperimentalTestApi::class)
class SystemSettingsTabScanningTest {

    @Test
    fun `the scanning row shows the label it is given`() = runComposeUiTest {
        setContent {
            MaterialTheme { ScanningRow("Scanning folder…") }
        }
        // The label is the whole point: a bare spinner says "busy" without saying at what, and this
        // sits exactly where the "No files detected" verdict would otherwise be.
        onNodeWithText("Scanning folder…").assertIsDisplayed()
    }
}
