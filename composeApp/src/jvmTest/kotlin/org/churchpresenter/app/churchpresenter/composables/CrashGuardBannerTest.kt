package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The strip that says the crash-loop guard turned video backgrounds off, with the two buttons that
 * turn them back on or hide the strip.
 *
 * Every parameter is pinned here rather than left to default. The defaults read the JVM-wide
 * `CrashReporter` singleton and the crash-count file under `~/.churchpresenter`, so a test that let
 * them stand would be asserting on process state another fork can change — and forcing that state
 * would mean the ad-hoc singleton seam `AGENT.md` rules out. **The defaults themselves are
 * therefore not covered here**; `CrashReporterStartupTest` in `:diagnostics` covers what they read.
 */
@OptIn(ExperimentalTestApi::class)
class CrashGuardBannerTest {

    /** Both labels are resource-backed, so wait for one to resolve before reading the tree. */
    private fun ComposeUiTest.awaitBanner() =
        waitUntilAtLeastOneExists(hasText(RE_ENABLE), timeoutMillis = RESOURCE_TIMEOUT_MS)

    /** The banner is the only thing in these trees with a click action, so this counts its buttons. */
    private fun ComposeUiTest.buttonCount() = onAllNodes(hasClickAction()).fetchSemanticsNodes().size

    @Test
    fun `a run that did not crash draws no banner`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CrashGuardBanner(
                    crashedLastRun = false,
                    videoBackgroundsDisabled = true,
                    consecutiveCrashes = 2,
                    onReEnable = {},
                )
            }
        }
        assertEquals(0, buttonCount(), "a clean previous run must not draw the banner")
    }

    @Test
    fun `a crash that left video backgrounds on draws no banner`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CrashGuardBanner(
                    crashedLastRun = true,
                    videoBackgroundsDisabled = false,
                    consecutiveCrashes = 2,
                    onReEnable = {},
                )
            }
        }
        assertEquals(
            0,
            buttonCount(),
            "the banner is about video backgrounds being off, so it must gate on that too",
        )
    }

    @Test
    fun `the message carries the crash count`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CrashGuardBanner(
                    crashedLastRun = true,
                    videoBackgroundsDisabled = true,
                    consecutiveCrashes = 4,
                    onReEnable = {},
                )
            }
        }
        awaitBanner()
        onNodeWithText("4", substring = true)
            .assertExists("the banner must say how many crashes in a row tripped the guard")
    }

    @Test
    fun `turning video backgrounds back on calls through and closes the banner`() = runComposeUiTest {
        var reEnabled = false
        setContent {
            MaterialTheme {
                CrashGuardBanner(
                    crashedLastRun = true,
                    videoBackgroundsDisabled = true,
                    consecutiveCrashes = 2,
                    onReEnable = { reEnabled = true },
                )
            }
        }
        awaitBanner()
        onNodeWithText(RE_ENABLE).performClick()

        assertTrue(reEnabled, "the re-enable button must call the caller's onReEnable")
        assertEquals(0, buttonCount(), "re-enabling must also close the banner, not just call back")
    }

    @Test
    fun `dismissing closes the banner without re-enabling anything`() = runComposeUiTest {
        var reEnabled = false
        setContent {
            MaterialTheme {
                CrashGuardBanner(
                    crashedLastRun = true,
                    videoBackgroundsDisabled = true,
                    consecutiveCrashes = 2,
                    onReEnable = { reEnabled = true },
                )
            }
        }
        awaitBanner()
        onNodeWithText(DISMISS).performClick()

        assertEquals(0, buttonCount(), "dismissing must hide the banner")
        assertFalse(reEnabled, "dismissing must leave video backgrounds off — it only hides the strip")
    }

    @Test
    fun `the modifier passed by the caller reaches the banner`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CrashGuardBanner(
                    modifier = Modifier.testTag(BANNER_TAG),
                    crashedLastRun = true,
                    videoBackgroundsDisabled = true,
                    consecutiveCrashes = 2,
                    onReEnable = {},
                )
            }
        }
        awaitBanner()
        onNodeWithTag(BANNER_TAG)
            .assertExists("the caller's modifier must be applied to the banner surface")
    }

    private companion object {
        const val RE_ENABLE = "Turn back on"
        const val DISMISS = "Dismiss"
        const val BANNER_TAG = "crash-guard-banner"
        const val RESOURCE_TIMEOUT_MS = 5_000L
    }
}
