@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.diagnostics.CrashReporter
import kotlin.test.Test
import kotlin.test.assertEquals

class CrashGuardBannerDefaultsTest {

    @Test
    fun `left to its defaults the banner follows the crash reporter`() = runComposeUiTest {
        val shown = CrashReporter.didCrashLastRun && CrashReporter.videoBackgroundsDisabled
        setContent { MaterialTheme { CrashGuardBanner() } }
        val buttons = { onAllNodes(hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).size }
        if (shown) waitUntil(timeoutMillis = 5_000L) { buttons() == 2 }
        waitForIdle()
        assertEquals(if (shown) 2 else 0, buttons())
    }
}
