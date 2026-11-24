package org.churchpresenter.hosanna.composables.media

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.hosanna.composables.foundation.HosannaStateAlpha
import org.churchpresenter.hosanna.composables.foundation.HosannaTheme
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.composables.defaults.ThumbnailDefaults
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.foundation.ThumbnailColors
import org.churchpresenter.hosanna.composables.foundation.ThumbnailLook
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ThumbnailLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailSize
import org.churchpresenter.hosanna.composables.model.ThumbnailStatus
import org.churchpresenter.hosanna.composables.foundation.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_broken_image
import org.churchpresenter.icons.generated.resources.ic_play
import org.churchpresenter.icons.generated.resources.ic_visibility
import org.churchpresenter.icons.generated.resources.ic_visibility_off
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

@Composable
internal fun BoxScope.ThumbnailContent(
    image: Painter?,
    look: ThumbnailLook,
    labels: ThumbnailLabels,
    onLocate: (() -> Unit)?,
) {
    when (look.status) {
        ThumbnailStatus.Loading -> Shine(look.colors)
        ThumbnailStatus.Missing -> Missing(look, labels, onLocate)
        ThumbnailStatus.Ready, ThumbnailStatus.Offline -> image?.let {
            val faded = look.hidden || look.status == ThumbnailStatus.Offline
            Image(
                it,
                null,
                Modifier
                    .fillMaxSize()
                    .alpha(
                        when {
                            look.hidden -> ThumbnailDefaults.HIDDEN_ALPHA
                            look.status == ThumbnailStatus.Offline -> ThumbnailDefaults.OFFLINE_ALPHA
                            else -> 1f
                        },
                    ),
                contentScale = ContentScale.Fit,
                colorFilter = if (faded) grayscale(look.hidden) else null,
            )
        }
    }
}

private fun grayscale(hidden: Boolean): ColorFilter {
    val saturation = if (hidden) ThumbnailDefaults.HIDDEN_SATURATION else 0f
    return ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
}

@Composable
private fun Shine(colors: ThumbnailColors) {
    val sweep by rememberInfiniteTransition().animateFloat(
        initialValue = -ThumbnailDefaults.SHINE_WIDTH,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(ThumbnailDefaults.SHINE_MILLIS, easing = LinearEasing),
            RepeatMode.Restart,
        ),
    )
    Box(
        Modifier.fillMaxSize().drawBehind {
            val start = sweep * size.width
            val end = start + ThumbnailDefaults.SHINE_WIDTH * size.width
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, colors.shine, Color.Transparent), start, end))
        },
    )
}

@Composable
private fun BoxScope.Missing(look: ThumbnailLook, labels: ThumbnailLabels, onLocate: (() -> Unit)?) {
    val ink = look.colors.placeholderInk
    Column(
        Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ThumbnailDefaults.MissingGap),
    ) {
        Image(
            painterResource(Res.drawable.ic_broken_image),
            null,
            Modifier.size(ThumbnailDefaults.MissingIcon),
            colorFilter = ColorFilter.tint(ink),
        )
        if (look.size != ThumbnailSize.Small) {
            if (look.hover && onLocate != null) {
                StandardButton(labels.file.locate, onLocate, hierarchy = ButtonHierarchy.Tertiary)
            } else {
                BasicText(labels.file.missing, style = HosannaTheme.typography.captionStrong.copy(color = ink))
            }
        }
    }
}

@Composable
internal fun BoxScope.ThumbnailOverlay(
    look: ThumbnailLook,
    labels: ThumbnailLabels,
    live: Boolean,
    number: String?,
    duration: String?,
    progress: Float?,
) {
    val colors = look.colors
    if (colors.scrim != Color.Transparent) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(ThumbnailDefaults.SCRIM_HEIGHT)
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.scrim))),
        )
    }
    progress?.let {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(it.coerceIn(0f, 1f))
                .height(ThumbnailDefaults.Progress)
                .background(ThemeColors.current.primary),
        )
    }
    val inset = Modifier.padding(ThumbnailDefaults.BadgeInset)
    val small = look.size == ThumbnailSize.Small
    if (live) {
        if (small) {
            Box(
                inset
                    .align(Alignment.TopStart)
                    .size(ThumbnailDefaults.LiveDot)
                    .background(ThemeColors.current.live, CircleShape),
            )
        } else {
            Badge(labels.live, ThemeColors.current.live, ThemeColors.current.onLive, inset.align(Alignment.TopStart))
        }
    }
    if (!small) {
        number?.let { Badge(it, colors.badge, colors.onBadge, inset.align(Alignment.BottomStart)) }
        duration?.let { Badge(it, colors.badge, colors.onBadge, inset.align(Alignment.BottomEnd)) }
    }
    if (look.status == ThumbnailStatus.Offline) {
        Badge(labels.file.offline, colors.badge, colors.onBadge, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Badge(text: String, background: Color, ink: Color, modifier: Modifier) {
    Box(
        modifier
            .height(ThumbnailDefaults.BadgeHeight)
            .background(background, RoundedCornerShape(ThumbnailDefaults.BadgeRadius))
            .padding(horizontal = ThumbnailDefaults.BadgePadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = HosannaTheme.typography.captionStrong.tabular().copy(color = ink))
    }
}

@Composable
internal fun BoxScope.ThumbnailActions(
    look: ThumbnailLook,
    labels: ThumbnailLabels,
    goLive: (() -> Unit)?,
    onHiddenChange: ((Boolean) -> Unit)?,
) {
    val eye = onHiddenChange?.takeIf { look.actions || look.hidden }
    val play = goLive?.takeIf { look.actions }
    if (eye == null && play == null) return
    Row(
        Modifier.align(Alignment.TopEnd).padding(ThumbnailDefaults.BadgeInset),
        horizontalArrangement = Arrangement.spacedBy(ThumbnailDefaults.ActionGap),
    ) {
        eye?.let {
            val icon = if (look.hidden) Res.drawable.ic_visibility_off else Res.drawable.ic_visibility
            OverlayButton(icon, if (look.hidden) labels.showAgain else labels.hide, look.colors) { it(!look.hidden) }
        }
        play?.let { OverlayButton(Res.drawable.ic_play, labels.goLive, look.colors, it) }
    }
}

@Composable
private fun OverlayButton(icon: DrawableResource, label: String, colors: ThumbnailColors, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Tooltip(label) {
        Box(
            Modifier
                .size(ThumbnailDefaults.Action)
                .background(if (hovered) colors.onBadge.overBadge(colors.badge) else colors.badge, CircleShape)
                .semantics { contentDescription = label }
                .clickable(source, null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(icon),
                null,
                Modifier.size(ThumbnailDefaults.ActionIcon),
                colorFilter = ColorFilter.tint(colors.onBadge),
            )
        }
    }
}

private fun Color.overBadge(badge: Color) = copy(alpha = HosannaStateAlpha.HOVER).compositeOver(badge)

@Composable
internal fun ThumbnailCaption(
    caption: String,
    subtitle: String?,
    hidden: Boolean,
    colors: ThumbnailColors,
    modifier: Modifier,
) {
    var cut by remember { mutableStateOf(false) }
    val style = HosannaTheme.typography.captionStrong
    Column(modifier) {
        Tooltip(caption, enabled = cut) {
            BasicText(
                caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { cut = it.hasVisualOverflow },
                style = style.copy(
                    color = if (hidden) colors.captionDim else colors.caption,
                    textDecoration = if (hidden) TextDecoration.LineThrough else null,
                ),
            )
        }
        subtitle?.let {
            BasicText(
                it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = HosannaTheme.typography.caption.copy(color = colors.captionDim),
            )
        }
    }
}
