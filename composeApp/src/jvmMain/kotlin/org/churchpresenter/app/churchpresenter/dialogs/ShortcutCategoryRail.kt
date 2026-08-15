package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.models.ShortcutScope

/** Wide enough for "Presentation Tab" at the rail's text size without ellipsis. */
internal val RAIL_WIDTH = 178.dp

private val RAIL_ITEM_SHAPE = RoundedCornerShape(8.dp)

/**
 * One entry in the rail.
 *
 * [scope] is null for the mouse section, which is the one category with no bindings behind it —
 * pointer gestures are hand-written rows, not registry entries, so there is no scope to name them.
 */
internal data class ShortcutCategory(
    val scope: ShortcutScope?,
    val title: String,
    val count: Int,
    val hasConflict: Boolean,
)

/**
 * The category picker down the left of the shortcuts dialog.
 *
 * The dialog used to draw every category at once in one long scroll, which meant ~45 rows between
 * the Menus heading and the Canvas one and no way to see what a tab answers to without hunting.
 * Selecting a category here narrows the list to it; searching ignores the selection and spans all
 * of them, which is why a searched row carries its category as a tag.
 *
 * The dot marks a category holding a binding that collides with another — the same conflicts the
 * toolbar counts, placed where you can see which part of the app they are in.
 */
@Composable
internal fun ShortcutCategoryRail(
    categories: List<ShortcutCategory>,
    selected: ShortcutScope?,
    enabled: Boolean,
    onSelect: (ShortcutScope?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(colors.surfaceVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            categories.forEach { category ->
                // Dimmed rather than hidden while a filter spans every category: the counts are still
                // worth reading, and a rail that emptied itself mid-search would be jarring.
                val active = enabled && category.scope == selected
                ShortcutCategoryItem(
                    category = category,
                    active = active,
                    muted = !enabled,
                    onClick = { onSelect(category.scope) },
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun ShortcutCategoryItem(
    category: ShortcutCategory,
    active: Boolean,
    muted: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = when {
        active -> colors.onSurface
        muted -> colors.onSurfaceVariant.copy(alpha = 0.6f)
        else -> colors.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(33.dp)
            .background(
                if (active) colors.secondaryContainer.copy(alpha = 0.7f) else Color.Transparent,
                RAIL_ITEM_SHAPE,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp)
            .testTag(shortcutCategoryTag(category.scope)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 15.dp)
                .background(
                    if (active) colors.primary else Color.Transparent,
                    RoundedCornerShape(2.dp),
                )
        )
        Text(
            text = category.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (category.hasConflict) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colors.error, RoundedCornerShape(3.dp))
            )
        }
        Text(
            text = category.count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = if (active) 0.9f else 0.6f),
        )
    }
}
