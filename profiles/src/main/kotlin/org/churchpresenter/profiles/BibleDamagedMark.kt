package org.churchpresenter.profiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.bible.checkSpbRows
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_warning
import org.churchpresenter.sharedui.composables.ConditionalTooltipArea
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_row_warning_tooltip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** The warning mark's size; the row reserves this much room for it while any Bible in the stack shows one. */
internal val DamagedMarkSize: Dp = 18.dp

/**
 * How many verse rows with an unreadable id each stacked Bible has, by file name.
 *
 * Only Bibles that have some are in the map. The files are read on `Dispatchers.IO`, so the page
 * paints at once and the marks arrive a moment later.
 */
@Composable
internal fun rememberDamagedCounts(storageDirectory: String, fileNames: List<String>): Map<String, Int> {
    val counts by produceState(emptyMap<String, Int>(), storageDirectory, fileNames) {
        value = withContext(Dispatchers.IO) { damagedCounts(storageDirectory, fileNames) }
    }
    return counts
}

internal fun damagedCounts(storageDirectory: String, fileNames: List<String>): Map<String, Int> =
    fileNames.mapNotNull { name ->
        val file = File(storageDirectory, name).takeIf { it.isFile } ?: return@mapNotNull null
        val total = runCatching { file.useLines(Charsets.UTF_8) { checkSpbRows(it).total } }.getOrDefault(0)
        if (total > 0) name to total else null
    }.toMap()

/** A warning icon that says on hover how many verses of this Bible cannot be shown. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DamagedVersesMark(count: Int) {
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = stringResource(Res.string.bible_row_warning_tooltip, count),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_warning),
            contentDescription = stringResource(Res.string.bible_row_warning_tooltip, count),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(DamagedMarkSize),
        )
    }
}
