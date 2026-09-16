@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.CrashGuardBanner
import kotlin.test.Test

/**
 * The crash-guard banner, which the app draws over the toolbar after repeated crashes turned video
 * backgrounds off.
 *
 * Shot on its own rather than through the app preview because the only way to reach it in the
 * running app is to crash twice in a row — so this is the one place a reviewer can look at it. Both
 * widths are here because the strip is a weighted message between two text buttons, and how it
 * degrades when the message no longer fits on one line is the part worth approving.
 *
 * The state is pinned through the parameters; nothing here reads the real crash count.
 */
class CrashGuardBannerScreenshotTest {

    @Test
    fun `the banner at a comfortable width`() {
        captureComponent(SECTION, "default", drive = { awaitBanner() }) { Banner(WIDE) }
    }

    @Test
    fun `the banner where the message has to wrap`() {
        captureComponent(SECTION, "narrow", drive = { awaitBanner() }) { Banner(NARROW) }
    }

    @Composable
    private fun Banner(width: Dp) {
        CrashGuardBanner(
            modifier = Modifier.width(width),
            crashedLastRun = true,
            videoBackgroundsDisabled = true,
            consecutiveCrashes = 2,
            onReEnable = {},
        )
    }

    private companion object {
        const val SECTION = "crashGuardBanner"
        val WIDE = 720.dp
        val NARROW = 360.dp
        const val RESOURCE_TIMEOUT_MS = 5_000L

        /** The labels are resource-backed; shoot only once they have resolved, never mid-load. */
        fun ComposeUiTest.awaitBanner() =
            waitUntilAtLeastOneExists(hasText("Turn back on"), timeoutMillis = RESOURCE_TIMEOUT_MS)
    }
}
