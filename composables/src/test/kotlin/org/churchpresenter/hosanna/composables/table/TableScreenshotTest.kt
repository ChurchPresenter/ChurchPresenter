package org.churchpresenter.hosanna.composables.table

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.list.ListPanel
import org.churchpresenter.hosanna.composables.list.RowAction
import org.churchpresenter.hosanna.composables.model.SortDirection
import org.churchpresenter.hosanna.composables.model.TableBulk
import org.churchpresenter.hosanna.composables.model.TableColumn
import org.churchpresenter.hosanna.composables.model.TableRow
import org.churchpresenter.hosanna.composables.model.TableSort
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TableScreenshotTest {

    private val section = "table/table"
    private val columns = listOf(
        TableColumn("number", "No.", width = 72.dp, numeric = true, secondary = true),
        TableColumn("title", "Title"),
        TableColumn("book", "Song Book", width = 160.dp, secondary = true),
    )
    private val rows = listOf(
        TableRow("1", listOf("1", "Amazing Grace", "Hymnal")),
        TableRow("2", listOf("2", "Be Thou My Vision", "Hymnal")),
        TableRow("3", listOf("3", "Blessed Assurance", "Songs of Praise")),
        TableRow("4", listOf("12", "Come Thou Fount of Every Blessing (with the extra verse)", "Hymnal")),
        TableRow("5", listOf("104", "Great Is Thy Faithfulness", "Songs of Praise")),
    )

    @Composable
    private fun Songs(
        sources: Map<String, MutableInteractionSource> = emptyMap(),
        selected: Set<String> = setOf("2"),
        compact: Boolean = false,
        bulk: TableBulk? = null,
        loading: Boolean = false,
        shown: List<TableRow> = rows,
    ) {
        ListPanel(Modifier.width(600.dp)) {
            Table(
                columns, shown,
                selectedKeys = selected,
                sort = TableSort("title", SortDirection.Ascending),
                compact = compact,
                bulk = bulk,
                loading = loading,
                rowInteractionSources = sources,
                keepActions = setOf("5"),
                rowActions = { key ->
                    RowAction(testIcon(), "Star $key", {})
                    RowAction(testIcon(), "Delete $key", {}, destructive = true)
                },
                empty = { TableEmpty("No songs match “zz”", actionText = "Clear search") },
            )
        }
    }

    @Test
    fun songs() = hosannaComponent(section, "songs") { Songs() }

    @Test
    fun states() = hosannaComponent(section, "states") {
        Songs(
            sources = mapOf(
                "1" to remember { frozenSource(ButtonState.Hovered) },
                "3" to remember { frozenSource(ButtonState.Pressed) },
                "4" to remember { frozenSource(ButtonState.Focused) },
            ),
        )
    }

    @Test
    fun compact() = hosannaComponent(section, "compact") { Songs(compact = true) }

    @Test
    fun bulk() = hosannaComponent(section, "bulk") {
        Songs(selected = emptySet(), bulk = TableBulk(setOf("2", "3"), { _, _ -> }, {}, "Select all"))
    }

    @Test
    fun loading() = hosannaComponent(section, "loading") { Songs(loading = true) }

    @Test
    fun empty() = hosannaComponent(section, "empty") { Songs(shown = emptyList()) }

    @Test
    fun headerHovered() = hosannaPopup(
        section, "headerHovered", keepHover = true,
        drive = { onNodeWithText("Song Book").performMouseInput { moveTo(center) } },
    ) { Songs() }
}
