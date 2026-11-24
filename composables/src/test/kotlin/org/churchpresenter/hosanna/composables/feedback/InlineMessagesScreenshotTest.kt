package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.HosannaTheme
import org.churchpresenter.hosanna.composables.model.MessageAction
import org.churchpresenter.hosanna.composables.model.MessageIntent
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

class InlineMessagesScreenshotTest {

    private val section = "feedback/inlineMessages"
    private val texts = mapOf(
        MessageIntent.Info to ("A screen is asking to join" to "Lobby TV wants to show your slides."),
        MessageIntent.Success to ("Screen approved" to "Lobby TV now shows the live slides."),
        MessageIntent.Warning to ("No session running" to "Start a session so people can send questions."),
        MessageIntent.Error to ("Upload failed" to "The ATEM stopped answering. Check the cable and try again."),
        MessageIntent.Neutral to ("Screen declined" to "Lobby TV was not added."),
    )

    @Test
    fun banners() = hosannaComponent(section, "banners") {
        Column(Modifier.width(640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            texts.forEach { (intent, text) ->
                Banner(
                    text.second, intent = intent, title = text.first,
                    action = MessageAction("Fix it") {},
                    otherActions = listOf(MessageAction("Details") {}),
                    dismiss = MessageAction("Dismiss") {},
                )
            }
        }
    }

    @Test
    fun narrow() = hosannaComponent(section, "narrow") {
        Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            texts.forEach { (intent, text) ->
                Banner(
                    text.second, intent = intent, title = text.first,
                    action = MessageAction("Fix it") {},
                    dismiss = MessageAction("Dismiss") {},
                )
            }
        }
    }

    @Test
    fun plain() = hosannaComponent(section, "plain") {
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Banner("Version 2.4 is ready to install.", intent = MessageIntent.Info)
            Banner("Pair this screen with the code below.", title = "Pairing code") {
                BasicText(
                    "4821",
                    style = HosannaTheme.typography.headline.copy(color = ThemeColors.current.onPrimaryContainer),
                )
            }
        }
    }

    @Test
    fun inline() = hosannaComponent(section, "inline") {
        Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            texts.forEach { (intent, text) -> InlineMessage(text.second, intent = intent) }
        }
    }

    @Test
    fun hint() = hosannaComponent(section, "hint") {
        Hint("Language names are set and edited in the Songs tab’s Languages menu.")
    }
}
