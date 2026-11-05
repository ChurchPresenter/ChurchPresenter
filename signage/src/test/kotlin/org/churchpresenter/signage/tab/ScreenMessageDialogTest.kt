@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The message dialog and the card of the message showing, used as an operator would. */
class ScreenMessageDialogTest {
    private val screens = listOf(
        MessageTarget("lobby", "Lobby", canShow = true),
        MessageTarget("kids", "Kids wing", canShow = true),
        MessageTarget("old", "Old TV", canShow = false),
    )

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        ChurchPresenterTheme(ThemeMode.LIGHT) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
    }

    private fun ComposeUiTest.dialog(shown: MutableList<SignageMessage>, current: SignageMessage? = null) = show {
        ScreenMessageDialog(
            current, screens, onShow = { shown += it }, onDismiss = {}, nowMs = { NOW }, zone = ZoneOffset.UTC,
        )
    }

    private fun ComposeUiTest.pick(shown: String, option: String) {
        onNodeWithText(shown).performClick()
        waitForIdle()
        onNode(hasText(option) and hasClickAction()).performClick()
        waitForIdle()
    }

    @Test
    fun `a full-screen message for half an hour goes to the screens left ticked`() = runComposeUiTest {
        val shown = mutableListOf<SignageMessage>()
        dialog(shown)
        onNodeWithText("Show").assertIsNotEnabled()
        field(MESSAGE_TEXT_TAG).performTextInput(" Parking lot B is full ")
        onNodeWithText("Show").assertIsEnabled()
        onNodeWithText("Full screen").performClick()
        pick("Until I clear it", "For 30 minutes")
        onNodeWithText("Its player is too old to show messages; update it first").assertExists()
        onNodeWithText("Old TV").performClick()
        onNodeWithText("Show").performClick()
        assertEquals(
            SignageMessage(
                "Parking lot B is full",
                fullScreen = true,
                untilMs = NOW + 30 * 60_000,
                screenIds = listOf("lobby", "kids"),
            ),
            shown.single(),
        )
    }

    @Test
    fun `a band can stop people using touch pages, and full screen always does without asking`() = runComposeUiTest {
        val shown = mutableListOf<SignageMessage>()
        dialog(shown)
        field(MESSAGE_TEXT_TAG).performTextInput("Doors open")
        onNodeWithText("Stop people using touch pages while it shows").assertIsOff().performClick()
        onNodeWithText("Stop people using touch pages while it shows").assertIsOn()
        onNodeWithText("Full screen").performClick()
        onNodeWithText("Stop people using touch pages while it shows").assertDoesNotExist()
        onNodeWithText("Band across the top").performClick()
        onNodeWithText("Show").performClick()
        assertEquals(true, shown.single().blockTouch)
    }

    @Test
    fun `a message changed to full screen keeps no touch setting of its own`() = runComposeUiTest {
        val shown = mutableListOf<SignageMessage>()
        dialog(shown, current = SignageMessage("Doors open", blockTouch = true))
        onNodeWithText("Stop people using touch pages while it shows").assertIsOn()
        onNodeWithText("Full screen").performClick()
        onNodeWithText("Show").performClick()
        assertEquals(false, shown.single().blockTouch)
    }

    @Test
    fun `every screen ticked means every screen, and none ticked cannot be shown`() = runComposeUiTest {
        val shown = mutableListOf<SignageMessage>()
        dialog(shown)
        field(MESSAGE_TEXT_TAG).performTextInput("Welcome")
        pick("Until I clear it", "For 1 hour")
        pick("For 1 hour", "For 2 hours")
        onNodeWithText("Show").performClick()
        assertNull(shown.single().screenIds)
        assertEquals(NOW + 2 * 3_600_000, shown.single().untilMs)
        screens.forEach { onNodeWithText(it.name).performClick() }
        onNodeWithText("Show").assertIsNotEnabled()
    }

    @Test
    fun `a message being changed keeps its end and its screens unless told otherwise`() = runComposeUiTest {
        val shown = mutableListOf<SignageMessage>()
        val current = SignageMessage("Doors open", untilMs = NOW + 3_600_000, screenIds = listOf("kids"))
        dialog(shown, current)
        onNodeWithText("Until $AT_SEVEN").assertExists()
        onNodeWithText("Show").performClick()
        assertEquals(current, shown.single())
    }

    @Test
    fun `the card says where and until when, and changes or clears it`() = runComposeUiTest {
        var edits = 0
        var clears = 0
        val message = SignageMessage("Doors open", untilMs = NOW + 3_600_000, screenIds = listOf("lobby", "old"))
        show {
            ActiveMessageCard(
                message, screens, onEdit = { edits++ }, onClear = { clears++ }, nowMs = NOW, zone = ZoneOffset.UTC,
            )
        }
        // The old TV is named but cannot show it, so it is not counted.
        onNodeWithText("Showing on 1 of 3 screens · until $AT_SEVEN").assertExists()
        onNodeWithText("Change").performClick()
        onNodeWithText("Clear").performClick()
        assertEquals(1 to 1, edits to clears)
    }

    @Test
    fun `a message that has run out has no card`() = runComposeUiTest {
        val old = SignageMessage("Old", untilMs = NOW)
        show { ActiveMessageCard(old, screens, {}, {}, nowMs = NOW, zone = ZoneOffset.UTC) }
        onNodeWithText("Old").assertDoesNotExist()
    }

    private companion object {
        /** 18:00 UTC on the day the tests are pinned to. */
        val NOW = Instant.parse("2026-11-05T18:00:00Z").toEpochMilli()

        /** An hour on, as this computer writes a time. */
        val AT_SEVEN: String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(NOW + 3_600_000))
    }
}
