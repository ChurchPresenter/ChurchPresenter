package org.churchpresenter.hosanna.composables.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.model.NavigationTab
import org.churchpresenter.hosanna.composables.model.TabMenuLabels
import org.churchpresenter.hosanna.composables.model.TabStyle
import org.churchpresenter.hosanna.composables.model.TabStyleLabels
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import org.churchpresenter.hosanna.composables.testing.settle
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TabVisibilityButtonScreenshotTest {

    private val section = "navigation/tabVisibilityButton"
    private val labels = TabMenuLabels(
        "Tab visibility", TabStyleLabels("Tab style", "Icon + text", "Text", "Icon"),
        "Show tabs", "Reset to default", "Move up", "Move down",
    )
    private val names = listOf("Bible", "Songs", "Pictures", "Presentation", "Media")

    @Composable
    private fun Menu(hidden: Set<String>, style: TabStyle = TabStyle.IconAndText) {
        Box(Modifier.padding(start = 220.dp)) {
            TabVisibilityButton(
                names.map { NavigationTab(it, it, testIcon()) }, hidden,
                { _, _ -> }, {}, {}, style, {}, labels,
            )
        }
    }

    private fun ComposeUiTest.open() {
        onNodeWithContentDescription("Tab visibility").performClick()
        settle()
    }

    private fun ComposeUiTest.row(name: String) =
        onNode(hasAnyDescendant(hasText(name)) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))

    @Test
    fun open() = hosannaPopup(section, "open", drive = { open() }) { Menu(setOf("Pictures")) }

    @Test
    fun hoveredRow() = hosannaPopup(
        section, "hoveredRow", keepHover = true,
        drive = {
            open()
            row("Songs").performMouseInput { moveTo(Offset(right - 40f, centerY)) }
        },
    ) { Menu(setOf("Pictures"), TabStyle.Text) }

    @Test
    fun lastVisible() = hosannaPopup(section, "lastVisible", drive = { open() }) {
        Menu(setOf("Bible", "Pictures", "Presentation", "Media"), TabStyle.Icon)
    }

    @Test
    fun dragging() = hosannaPopup(
        section, "dragging", keepHover = true,
        drive = {
            open()
            row("Songs").performMouseInput {
                moveTo(Offset(right - 18.dp.toPx(), centerY))
                press()
                moveBy(Offset(0f, 12.dp.toPx()))
            }
        },
    ) { Menu(emptySet()) }
}
