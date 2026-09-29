package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.mergeColumnOrder
import org.churchpresenter.settings.SongColumnId

internal class SongTableColumns(
    private val density: Density,
    initialWidths: Map<String, Float>,
    initialOrder: List<String>,
    initialHidden: Set<String>,
) {
    private var widths by mutableStateOf(initialWidths)

    var order by mutableStateOf(initialOrder)
    var hidden by mutableStateOf(initialHidden)

    var draggingId by mutableStateOf<String?>(null)
    var dragAccumX by mutableStateOf(0f)

    var showMenu by mutableStateOf(false)
    var menuOffset by mutableStateOf(DpOffset.Zero)
    var showColumnsMenu by mutableStateOf(false)

    /**
     * Recomputed on every read rather than remembered: `remember(key)` makes a new `MutableState`
     * on each settings save, which would silently break a `derivedStateOf` subscribed to it.
     */
    val visible: List<String> get() = order.filter { it !in hidden }

    /** Action columns are a fixed 6dp spacer plus a 24dp icon button, so they have no stored width. */
    fun widthOf(id: String): Float =
        widths[id] ?: with(density) { 30.dp.toPx() }

    fun setWidth(id: String, px: Float) {
        val floor = MIN_WIDTHS[id] ?: return
        widths = widths + (id to px.coerceAtLeast(with(density) { floor.dp.toPx() }))
    }

    /** The widths as whole dp, for writing back to settings. */
    fun widthsInDp(): Map<String, Int> =
        widths.mapValues { (_, px) -> with(density) { px.toDp().value.toInt() } }

    private companion object {
        val MIN_WIDTHS = mapOf(
            SongColumnId.NUMBER to 30, SongColumnId.TITLE to 60, SongColumnId.SONGBOOK to 40, SongColumnId.TUNE to 40,
            SongColumnId.PLAY_COUNT to 30, SongColumnId.AUTHOR to 40, SongColumnId.COMPOSER to 40,
        )
    }
}

/**
 * The song table's column layout: widths, order, which are hidden, and the transient drag and menu
 * state that goes with rearranging them.
 *
 * Keyed the same way the individual `remember`s were, so a settings save still rebuilds it — the
 * widths come back from settings and the drag state starts clean, which is what it did before.
 */
@Composable
internal fun rememberSongTableColumns(
    settings: AppSettings,
    density: Density,
    availableColumns: List<String>,
): SongTableColumns = remember(
    settings.songSettings.colWidthNumber, settings.songSettings.colWidthTitle,
    settings.songSettings.colWidthSongbook, settings.songSettings.colWidthTune,
    settings.songSettings.colWidthPlayCount, settings.songSettings.colWidthAuthor,
    settings.songSettings.colWidthComposer,
    settings.songColOrder, settings.songHiddenCols, availableColumns,
) {
    SongTableColumns(
        density = density,
        initialWidths = with(density) {
            mapOf(
                SongColumnId.NUMBER to settings.songSettings.colWidthNumber.dp.toPx(),
                SongColumnId.TITLE to settings.songSettings.colWidthTitle.dp.toPx(),
                SongColumnId.SONGBOOK to settings.songSettings.colWidthSongbook.dp.toPx(),
                SongColumnId.TUNE to settings.songSettings.colWidthTune.dp.toPx(),
                SongColumnId.PLAY_COUNT to settings.songSettings.colWidthPlayCount.dp.toPx(),
                SongColumnId.AUTHOR to settings.songSettings.colWidthAuthor.dp.toPx(),
                SongColumnId.COMPOSER to settings.songSettings.colWidthComposer.dp.toPx(),
            )
        },
        initialOrder = mergeColumnOrder(settings.songColOrder, availableColumns),
        initialHidden = settings.songHiddenCols,
    )
}
