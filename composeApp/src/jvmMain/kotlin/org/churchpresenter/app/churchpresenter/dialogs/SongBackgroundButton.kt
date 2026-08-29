/*
 * The Background button as the design draws it — a tinted chip carrying a 26x18 preview of what the
 * song is currently set to, the word Background, and a caret that turns when its panel is open —
 * and the panel anchored under it.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_background
import org.churchpresenter.core.models.songs.SongBackground
import org.jetbrains.compose.resources.stringResource

internal const val SONG_BACKGROUND_BUTTON_TAG = "song_background_button"

/**
 * The chip and its panel together. [expanded] is held by the caller so the editor decides when the
 * panel closes — saving the song closes it too.
 */
@Composable
@Suppress("LongParameterList")
internal fun SongBackgroundButton(
    background: SongBackground,
    lowerThirdBackground: SongBackground,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBackgroundChange: (SongBackground) -> Unit,
    onLowerThirdBackgroundChange: (SongBackground) -> Unit,
    sampleLine: String,
    onApplyToSongbook: (() -> Unit)?,
) {
    val custom = background.isCustom || lowerThirdBackground.isCustom
    val shown = if (background.isCustom || !lowerThirdBackground.isCustom) background else lowerThirdBackground
    val tint = when {
        expanded -> MaterialTheme.colorScheme.primary
        custom -> MaterialTheme.colorScheme.primary.copy(alpha = QUIET_TINT_ALPHA)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val ink = if (custom || expanded) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant
    val caret by animateFloatAsState(if (expanded) CARET_OPEN_DEGREES else 0f, label = "bgCaret")

    Box {
        Row(
            modifier = Modifier
                .testTag(SONG_BACKGROUND_BUTTON_TAG)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (expanded || custom) MaterialTheme.colorScheme.primary.copy(alpha = CHIP_FILL_ALPHA)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .border(1.dp, tint, RoundedCornerShape(8.dp))
                .clickable { onExpandedChange(!expanded) }
                .padding(start = 5.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BackgroundChipSwatch(shown)
            Text(
                text = stringResource(Res.string.song_background),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = ink,
            )
            Text(
                text = "⌄",
                fontSize = 11.sp,
                color = ink,
                modifier = Modifier.rotate(caret),
            )
        }
        if (expanded) {
            Popup(
                popupPositionProvider = remember { SongBackgroundPanelPosition },
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true),
            ) {
                Box(Modifier.size(SONG_BACKGROUND_PANEL_WIDTH, SONG_BACKGROUND_PANEL_HEIGHT)) {
                    SongBackgroundPanel(
                        background = background,
                        lowerThirdBackground = lowerThirdBackground,
                        onBackgroundChange = onBackgroundChange,
                        onLowerThirdBackgroundChange = onLowerThirdBackgroundChange,
                        sampleLine = sampleLine,
                        onApplyToSongbook = onApplyToSongbook,
                        onDismiss = { onExpandedChange(false) },
                    )
                }
            }
        }
    }
}

/** The chip's own 26x18 preview; an inheriting song gets the design's diagonal stroke instead. */
@Composable
private fun BackgroundChipSwatch(background: SongBackground) {
    Box(
        modifier = Modifier.size(width = 26.dp, height = 18.dp)
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)),
    ) {
        SongBackgroundFill(background, Modifier.fillMaxSize())
        if (!background.isCustom) {
            val stroke = MaterialTheme.colorScheme.outline
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawLine(
                        color = stroke,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            )
        }
    }
}

/**
 * Where the panel sits: under the chip and right-aligned to it, pulled back on screen when there is
 * not room, and lifted to sit above the chip when there is no room below — the same rules the
 * design's own positioning code applies.
 */
private object SongBackgroundPanelPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = GAP_PX
        val pad = EDGE_PAD_PX
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(pad, (windowSize.width - popupContentSize.width - pad).coerceAtLeast(pad))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height + pad <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - gap - popupContentSize.height)
                .coerceAtLeast((windowSize.height - popupContentSize.height - pad).coerceAtLeast(pad))
        }
        return IntOffset(x, y)
    }
}

private const val GAP_PX = 6
private const val EDGE_PAD_PX = 12
private const val CARET_OPEN_DEGREES = 180f
private const val CHIP_FILL_ALPHA = 0.14f
private const val QUIET_TINT_ALPHA = 0.55f
