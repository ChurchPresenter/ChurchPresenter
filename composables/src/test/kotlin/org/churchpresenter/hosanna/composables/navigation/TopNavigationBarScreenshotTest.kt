package org.churchpresenter.hosanna.composables.navigation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.NavigationTab
import org.churchpresenter.hosanna.composables.model.TabStyle
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test

class TopNavigationBarScreenshotTest {

    private val section = "navigation/topNavigationBar"

    @Composable
    private fun tabs() = listOf("Bible", "Songs", "Pictures", "Presentation", "Media", "Q&A").map {
        NavigationTab(it.lowercase(), it, testIcon(), badge = if (it == "Q&A") "3" else null)
    }

    @Composable
    private fun Bar(
        width: Int = 760,
        style: TabStyle = TabStyle.IconAndText,
        sources: Map<String, MutableInteractionSource> = emptyMap(),
        actions: Boolean = true,
    ) {
        TopNavigationBar(
            tabs(), "songs", {}, Modifier.width(width.dp), style, sources,
            actions = if (actions) {
                {
                    listOf("Background", "Settings").forEach { name ->
                        IconButton(
                            testIcon(), name, {},
                            hierarchy = ButtonHierarchy.Tertiary, intent = ButtonIntent.Neutral,
                        )
                    }
                }
            } else {
                null
            },
        )
    }

    @Composable
    private fun frozenTabs(state: ButtonState): Map<String, MutableInteractionSource> =
        listOf("bible", "songs").associateWith { remember { frozenSource(state) } }

    @Test
    fun styles() = hosannaComponent(section, "styles") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Bar()
            Bar(style = TabStyle.Text)
            Bar(style = TabStyle.Icon)
            Bar(actions = false)
        }
    }

    @Test
    fun states() = hosannaComponent(section, "states") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(ButtonState.Hovered, ButtonState.Pressed, ButtonState.Focused).forEach { state ->
                Bar(sources = frozenTabs(state))
                Bar(style = TabStyle.Icon, sources = frozenTabs(state))
            }
        }
    }

    @Test
    fun narrow() = hosannaComponent(section, "narrow") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Bar(width = 520)
            Bar(width = 280)
        }
    }
}
