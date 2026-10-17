package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Launch at login switch over a stand-in for the OS, through [LocalAutoStart]: a change that
 * did not take leaves the switch where it was and says why, until one does. Each wait is on the
 * text or the switch state the change produces; the timeout only fails a stuck test.
 */
@OptIn(ExperimentalTestApi::class)
class LaunchAtLoginTest {

    /** Answers each change with the next of [answers], starting unregistered, and keeps what it was asked. */
    private class ScriptedSwitch(vararg answers: AutoStartChange) : AutoStartSwitch {
        private val answers = ArrayDeque(answers.toList())
        val asked: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())

        override fun isEnabled() = false

        override fun setEnabled(enabled: Boolean): AutoStartChange {
            asked += enabled
            return answers.removeFirst()
        }
    }

    private fun ComposeUiTest.showTab(switch: AutoStartSwitch) = setContent {
        CompositionLocalProvider(LocalAutoStart provides switch) {
            MaterialTheme { SystemSettingsTab(settings = AppSettings()) }
        }
    }

    private val launchSwitch = isToggleable() and hasText(LAUNCH_AT_LOGIN)

    private fun ComposeUiTest.pressLaunchSwitch() = onNode(launchSwitch).performScrollTo().performClick()

    private fun ComposeUiTest.awaitText(text: String, shown: Boolean = true) = waitUntil(timeoutMillis = WAIT_MS) {
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() == shown
    }

    @Test
    fun `a change Windows refuses says why, and the switch stays off`() = runComposeUiTest {
        val switch = ScriptedSwitch(AutoStartChange.REFUSED)
        showTab(switch)

        pressLaunchSwitch()
        awaitText(REFUSED)

        onNode(launchSwitch).assertIsOff()
        assertEquals(listOf(true), switch.asked)
    }

    @Test
    fun `any other failure says the setting could not be changed`() = runComposeUiTest {
        showTab(ScriptedSwitch(AutoStartChange.FAILED))

        pressLaunchSwitch()
        awaitText(FAILED)

        onNode(launchSwitch).assertIsOff()
    }

    @Test
    fun `a change that then goes through turns the switch on and clears the message`() = runComposeUiTest {
        showTab(ScriptedSwitch(AutoStartChange.REFUSED, AutoStartChange.DONE))
        pressLaunchSwitch()
        awaitText(REFUSED)

        pressLaunchSwitch()
        awaitText(REFUSED, shown = false)

        onNode(launchSwitch).assertIsOn()
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val LAUNCH_AT_LOGIN = "Launch at login"
        const val REFUSED = "Windows blocked this change"
        const val FAILED = "This setting could not be changed"
    }
}
