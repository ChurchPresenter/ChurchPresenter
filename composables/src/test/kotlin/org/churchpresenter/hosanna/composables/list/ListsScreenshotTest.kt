package org.churchpresenter.hosanna.composables.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.chip.Tag
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.media.Thumbnail
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.hosanna.composables.model.NavigationItem
import org.churchpresenter.hosanna.composables.model.ThumbnailFileLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailSize
import org.churchpresenter.hosanna.composables.selection.Checkbox
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import org.churchpresenter.hosanna.composables.testing.testIcon
import org.churchpresenter.hosanna.composables.testing.testSlide
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ListsScreenshotTest {

    private val section = "list/lists"
    private val books = listOf("Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua")
        .map { NavigationItem(it.lowercase(), it) }
    private val chapters = (1..6).map { NavigationItem("$it", "$it") }
    private val thumbLabels = ThumbnailLabels(
        "Go live", "Hide", "Show", "Hidden", "LIVE",
        ThumbnailFileLabels("Missing", "Locate", "Offline"),
    )

    @Composable
    private fun Actions(name: String) {
        RowAction(testIcon(), "Show $name", {})
        RowAction(testIcon(), "Delete $name", {}, destructive = true)
    }

    @Composable
    private fun StateRow(label: String, state: ButtonState, selected: Boolean = false, live: Boolean = false) {
        ListRow(
            label, {}, interactionSource = remember { frozenSource(state) },
            supporting = "Could you say more about the passage in Romans?",
            leading = { ListMeta("06:32") },
            trailing = { Tag("Approved", color = ChipColor.Neutral) },
            actions = { Actions(label) },
            selected = selected, liveTag = if (live) "LIVE" else null,
            enabled = state != ButtonState.Disabled,
        )
    }

    @Test
    fun sizes() = hosannaComponent(section, "sizes") {
        ListPanel(Modifier.width(480.dp)) {
            Column {
                ListRow("Amazing Grace", {}, leading = { ListMeta("42") }, trailing = { ListMeta("Hymnal") })
                ListRow(
                    "Lobby", {},
                    leading = { Checkbox(true, {}, "") },
                    trailing = { Tag("Online", color = ChipColor.Song) },
                )
                ListDivider()
                ListRow(
                    "Amazing Grace", {}, supporting = "Hymnal · 4 verses",
                    leading = { ListIconTile(testIcon()) }, trailing = { Tag("Song", color = ChipColor.Song) },
                )
                ListDivider()
                ListRow(
                    "Sermon.pptx", {}, supporting = "24 slides",
                    leading = { Thumbnail(testSlide(), {}, thumbLabels, size = ThumbnailSize.Small) },
                )
                ListDivider()
                ListRow(
                    "06:32  Could you say more about the passage in Romans?", {},
                    supporting = "Asked by a guest · 3 votes. Long questions wrap to two lines here and then end " +
                        "with an ellipsis when they run longer than that.",
                    threeLine = true, trailing = { Tag("Approved") },
                )
            }
        }
    }

    @Test
    fun states() = hosannaComponent(section, "states") {
        ListPanel(Modifier.width(520.dp)) {
            Column {
                StateRow("Rest", ButtonState.Rest)
                StateRow("Hovered", ButtonState.Hovered)
                StateRow("Pressed", ButtonState.Pressed)
                StateRow("Focused", ButtonState.Focused)
                StateRow("Selected", ButtonState.Rest, selected = true)
                StateRow("On screen", ButtonState.Rest, live = true)
                StateRow("Disabled", ButtonState.Disabled)
            }
        }
    }

    @Test
    fun dragging() = hosannaComponent(section, "dragging") {
        ListPanel(Modifier.width(420.dp)) {
            Column {
                ListRow("Welcome", {}, supporting = "Announcement")
                ListDropIndicator()
                ListRow("Amazing Grace", {}, supporting = "Song")
                ListRow("Romans 8", {}, supporting = "Bible", dragging = true)
            }
        }
    }

    private fun ComposeUiTest.hover(text: String) {
        onNodeWithText(text).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(100)
    }

    @Test
    fun navigation() = hosannaPopup(section, "navigation", keepHover = true, drive = { hover("Numbers") }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ListPanel(Modifier.width(180.dp).height(240.dp)) { NavigationList(books, "exodus", {}) }
            ListPanel(Modifier.width(64.dp).height(240.dp)) { NavigationList(chapters, "3", {}, centered = true) }
        }
    }
}
