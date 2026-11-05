@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.screenshot.captureComponent
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

/** A message for the screens, in both themes: being written, and showing above the screen cards. */
class SignageMessageScreenshotTest {
    private val screens = listOf(
        MessageTarget("lobby", "Lobby", canShow = true),
        MessageTarget("kids", "Kids wing", canShow = true),
        MessageTarget("old", "Old TV", canShow = false),
    )

    /** A band for an hour, to every screen but the one too old to show it. */
    @Test
    fun `a message being written`() = captureComponent(SECTION, "message_dialog", rootIndex = 1, drive = {
        field(MESSAGE_TEXT_TAG).performTextInput("Service starts in 5 minutes")
        onNodeWithText("Old TV").performClick()
        waitForIdle()
    }) {
        ScreenMessageDialog(null, screens, onShow = {}, onDismiss = {}, nowMs = { NOW }, zone = ZoneOffset.UTC)
    }

    @Test
    fun `a message showing`() = captureComponent(SECTION, "message_card") {
        Box(Modifier.width(CARD_WIDTH.dp)) {
            ActiveMessageCard(
                SignageMessage("Parking lot B is full", untilMs = NOW + 3_600_000, screenIds = listOf("lobby", "kids")),
                screens,
                onEdit = {},
                onClear = {},
                nowMs = NOW,
                zone = ZoneOffset.UTC,
            )
        }
    }

    private companion object {
        const val SECTION = "signage-message"
        const val CARD_WIDTH = 560
        val NOW = Instant.parse("2026-11-05T18:00:00Z").toEpochMilli()
    }
}
