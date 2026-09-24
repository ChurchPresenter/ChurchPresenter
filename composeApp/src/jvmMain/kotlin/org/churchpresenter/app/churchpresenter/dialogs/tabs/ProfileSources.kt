package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.bibleTranslationPositions
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.dropdownField

/**
 * The pieces the Profiles tab's Sources row is built from: the closed field both sources draw, and
 * the list arithmetic behind the Bible source's ordered list.
 */

/** Dimmed rather than hidden while a source is off, so the field still says what it is. */
private const val OFF_ALPHA = 0.6f

internal val SOURCE_FIELD_HEIGHT = 48.dp

/**
 * The stack positions a profile draws, in the order it draws them -- nothing while scripture is off.
 *
 * An empty stored list means "the whole stack, in stack order", which is what an untouched profile
 * holds; see [bibleTranslationPositions].
 */
internal fun shownBiblePositions(profile: OutputProfile, stackSize: Int): List<Int> =
    if (!profile.showBible) emptyList() else profile.bibleTranslationPositions(stackSize)

/**
 * [profile] drawing exactly [positions], in that order.
 *
 * Nothing left switches scripture off rather than storing an empty list, because an empty list is
 * read as "all of them". The whole stack in stack order is stored as that empty list, so a
 * translation added to the stack later still reaches a profile that was showing everything.
 */
internal fun withBiblePositions(profile: OutputProfile, positions: List<Int>, stackSize: Int): OutputProfile {
    if (positions.isEmpty()) return profile.copy(bibleMode = Constants.SONG_LANG_OFF, bibleTranslations = emptyList())
    val stored = if (positions == (0 until stackSize).toList()) emptyList() else positions
    val mode = if (profile.showBible) profile.bibleMode else Constants.SONG_LANG_BOTH
    return profile.copy(bibleMode = mode, bibleTranslations = stored)
}

/** [list] with the entries at [from] and [to] swapped, or unchanged when either is out of range. */
internal fun <T> swapped(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices || to !in list.indices) return list
    return list.toMutableList().also { it[from] = list[to]; it[to] = list[from] }
}

/**
 * A source's closed field: its name, what it is set to, a line under that, and a chevron -- the
 * same sunken well as the other dropdowns in the dialog.
 */
@Composable
internal fun SourceField(
    label: String,
    value: String,
    sub: String,
    open: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SOURCE_FIELD_HEIGHT)
            .dropdownField(shape, open)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).alpha(if (dimmed) OFF_ALPHA else 1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
