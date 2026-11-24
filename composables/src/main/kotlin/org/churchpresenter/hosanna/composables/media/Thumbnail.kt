package org.churchpresenter.hosanna.composables.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.ThumbnailDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.ThumbnailLook
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.foundation.thumbnailColors
import org.churchpresenter.hosanna.composables.model.ThumbnailLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailSize
import org.churchpresenter.hosanna.composables.model.ThumbnailStatus
import org.churchpresenter.hosanna.composables.model.canGoLive
import org.churchpresenter.hosanna.composables.model.showsHoverActions

private fun ThumbnailStatus.showsImage() = this == ThumbnailStatus.Ready || this == ThumbnailStatus.Offline

/**
 * A 16:9 tile for a slide, picture, video or screen. A click selects it; a double-click, Shift+Enter or the
 * hover button puts it live.
 *
 * @param image the picture, fitted inside without cropping; null while loading or missing.
 * @param onClick called when it is clicked, to select it.
 * @param labels the tile's texts.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param size the tile's size.
 * @param caption optional text under the tile, also its screen-reader name.
 * @param subtitle an optional second caption line.
 * @param selected marks it as picked for preview.
 * @param live marks it as on screen now; wins over [selected].
 * @param number an optional slide number badge.
 * @param duration an optional duration badge.
 * @param progress an optional playback bar from 0 to 1.
 * @param status whether the picture is ready, loading, missing or of an offline screen.
 * @param hidden fades it and skips it when presenting; it cannot go live.
 * @param onGoLive turns on going live.
 * @param onHiddenChange turns on the hide button and is called with the new state.
 * @param onLocate turns on the Locate button on a missing file.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    image: Painter?,
    onClick: () -> Unit,
    labels: ThumbnailLabels,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    size: ThumbnailSize = ThumbnailSize.Medium,
    caption: String? = null,
    subtitle: String? = null,
    selected: Boolean = false,
    live: Boolean = false,
    number: String? = null,
    duration: String? = null,
    progress: Float? = null,
    status: ThumbnailStatus = ThumbnailStatus.Ready,
    hidden: Boolean = false,
    onGoLive: (() -> Unit)? = null,
    onHiddenChange: ((Boolean) -> Unit)? = null,
    onLocate: (() -> Unit)? = null,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(true, false, hovered, pressed, focused)
    val colors = HosannaTheme.colors.thumbnailColors(selected, live, state)
    val metrics = ThumbnailDefaults.metrics(size)
    val shape = RoundedCornerShape(metrics.radius)
    val goLive = onGoLive?.takeIf { canGoLive(status, hidden) }
    val currentGoLive by rememberUpdatedState(goLive)
    val active = state == ButtonState.Hovered || state == ButtonState.Pressed || state == ButtonState.Focused
    val look = ThumbnailLook(size, status, hidden, colors, active && showsHoverActions(size, status), active)
    Column(modifier.width(metrics.width)) {
        Box(
            Modifier
                .size(metrics.width, metrics.height)
                .then(colors.ring?.let { Modifier.focusRing(it, metrics.radius) } ?: Modifier)
                .onPreviewKeyEvent { event ->
                    val shiftEnter = event.key == Key.Enter && event.isShiftPressed &&
                        event.type == KeyEventType.KeyDown
                    shiftEnter && currentGoLive?.let { it(); true } == true
                }
                .semantics {
                    this.selected = selected
                    caption?.let { contentDescription = it }
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onDoubleClick = goLive,
                    onClick = onClick,
                )
                .clip(shape)
                .background(if (status.showsImage()) colors.letterbox else colors.placeholder),
        ) {
            ThumbnailContent(image, look, labels, onLocate)
            ThumbnailOverlay(look, labels, live, number, duration, progress)
            ThumbnailActions(look, labels, goLive, onHiddenChange)
            Box(
                Modifier
                    .matchParentSize()
                    .border(ThumbnailDefaults.Edge, colors.edge, shape)
                    .then(colors.border?.let { Modifier.border(ThumbnailDefaults.Border, it, shape) } ?: Modifier),
            )
        }
        if (caption != null) {
            ThumbnailCaption(
                caption,
                if (hidden) labels.hiddenNote else subtitle,
                hidden,
                colors,
                Modifier.padding(top = ThumbnailDefaults.CaptionGap),
            )
        }
    }
}
