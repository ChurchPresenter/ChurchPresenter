package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.EmptyStateDefaults
import org.churchpresenter.hosanna.composables.foundation.ThemeColors
import org.churchpresenter.hosanna.composables.model.EmptyStateTone

/**
 * What a panel shows when it has nothing yet: a tinted icon tile, a title that says what is
 * missing, a line on what to do next and the buttons that do it, centered in the space it is given.
 *
 * @param icon the tile's icon.
 * @param title says what is missing, such as "No folder selected".
 * @param modifier applied to the outermost layout; give it the panel's size to center in it.
 * @param body one or two lines on what to do next.
 * @param tone what kind of empty it is, shown by the tile's color.
 * @param compact the smaller tile and type, for narrow panels and lists.
 * @param hint an optional quiet line under the buttons.
 * @param actions the buttons, such as one [org.churchpresenter.hosanna.composables.button.StandardButton].
 */
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    tone: EmptyStateTone = EmptyStateTone.Start,
    compact: Boolean = false,
    hint: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = ThemeColors.current
    val typography = HosannaTheme.typography
    val metrics = EmptyStateDefaults.metrics(compact)
    val (tile, ink) = tileColors(tone)
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = EmptyStateDefaults.MaxWidth + metrics.padding * 2).padding(metrics.padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(metrics.tile)
                    .background(tile, if (compact) HosannaTheme.shapes.l else HosannaTheme.shapes.xl),
                contentAlignment = Alignment.Center,
            ) {
                Image(icon, null, Modifier.size(metrics.icon), colorFilter = ColorFilter.tint(ink))
            }
            Spacer(Modifier.height(metrics.tileGap))
            BasicText(
                title,
                style = (if (compact) typography.subtitle else typography.title)
                    .copy(color = colors.onSurface, textAlign = TextAlign.Center),
            )
            body?.let {
                Spacer(Modifier.height(metrics.bodyGap))
                BasicText(
                    it,
                    style = (if (compact) typography.caption else typography.body)
                        .copy(color = colors.onSurfaceVariant, textAlign = TextAlign.Center),
                )
            }
            actions?.let {
                Spacer(Modifier.height(metrics.actionsGap))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EmptyStateDefaults.ButtonGap),
                    verticalAlignment = Alignment.CenterVertically,
                    content = it,
                )
            }
            hint?.let {
                Spacer(Modifier.height(EmptyStateDefaults.HintGap))
                BasicText(it, style = typography.caption.copy(color = colors.faint, textAlign = TextAlign.Center))
            }
        }
    }
}

@Composable
private fun tileColors(tone: EmptyStateTone): Pair<Color, Color> {
    val colors = ThemeColors.current
    return when (tone) {
        EmptyStateTone.Start -> colors.primaryContainer to colors.onPrimaryContainer
        EmptyStateTone.Neutral -> colors.secondaryContainer to colors.onSurfaceVariant
        EmptyStateTone.Error -> colors.errorContainer to colors.error
    }
}
