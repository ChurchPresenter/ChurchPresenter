package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.sectionOrderAbbreviation
import org.churchpresenter.core.models.songs.sectionOrderLabel
import org.churchpresenter.theme.AppShape

/** How many sections a song list's Order cell shows before it says how many more there are. */
const val ORDER_TOKENS_SHOWN = 5

/**
 * A song's order as a row of coloured chips, one per section in the order it is sung — the first
 * [ORDER_TOKENS_SHOWN], then `+n` — or [asWrittenLabel] when the song has no order of its own.
 */
@Composable
fun SectionOrderTokens(order: List<String>, asWrittenLabel: String, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (order.isEmpty()) {
            Text(text = asWrittenLabel, fontSize = 11.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@Row
        }
        order.take(ORDER_TOKENS_SHOWN).forEach { key ->
            val ink = SectionInk.of(sectionKindOf(sectionOrderLabel(key)))
            Text(
                text = sectionOrderAbbreviation(key),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.background(ink.copy(alpha = TOKEN_FILL_ALPHA), AppShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (order.size > ORDER_TOKENS_SHOWN) {
            Text(
                text = "+${order.size - ORDER_TOKENS_SHOWN}",
                fontSize = 11.sp,
                color = muted,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private const val TOKEN_FILL_ALPHA = 0.16f
