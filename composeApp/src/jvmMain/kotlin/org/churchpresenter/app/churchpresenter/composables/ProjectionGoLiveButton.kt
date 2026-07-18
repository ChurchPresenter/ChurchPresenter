package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import churchpresenter.composeapp.generated.resources.projection_all_displays
import churchpresenter.composeapp.generated.resources.projection_all_displays_sub
import churchpresenter.composeapp.generated.resources.projection_display_num
import churchpresenter.composeapp.generated.resources.projection_display_hash
import churchpresenter.composeapp.generated.resources.projection_go_live_disabled
import churchpresenter.composeapp.generated.resources.projection_go_live_label
import churchpresenter.composeapp.generated.resources.projection_live_label
import churchpresenter.composeapp.generated.resources.projection_output_display_header
import churchpresenter.composeapp.generated.resources.projection_simulated_output
import churchpresenter.composeapp.generated.resources.projection_target_all_short
import churchpresenter.composeapp.generated.resources.projection_type_lower_third
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.models.GoLiveTarget
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.utils.DevFlags
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

// Design palette (converted from the source oklch design to sRGB). Decimal r,g,b[,a] 0-255.
private val IdleMainBg = Color(0, 47, 61)
private val LiveMainBg = Color(0, 125, 0)
private val IdleMainText = Color(185, 190, 196)
private val IdleDot = Color(90, 94, 99)
private val IdleTriggerLabel = Color(95, 100, 105)
private val LiveTriggerLabel = Color(0, 234, 0, 204)
private val IdleTypeColor = Color(168, 164, 159)
private val LiveTypeColor = Color(224, 229, 235)
private val IdleDivider = Color(48, 59, 74)
private val LiveDivider = Color(0, 90, 0, 128)
private val PulseColor = Color(0, 163, 39, 140)

private val DropBg = Color(9, 16, 25)
private val DropBorder = Color(36, 47, 61)
private val HeaderBorder = Color(25, 32, 41)
private val HeaderText = Color(68, 72, 77)
private val RowHover = Color(18, 27, 39)
private val RowSelected = Color(13, 23, 35)
private val CheckGreen = Color(38, 182, 61)
private val TextSelected = Color(225, 221, 216)
private val TextUnselected = Color(186, 183, 178)
private val SubSelected = Color(95, 100, 105)
private val SubUnselected = Color(68, 72, 77)
private val DisabledLabel = Color(224, 132, 120)

private val BlueIcon = Color(0, 176, 236)
private val BlueIconBg = Color(4, 35, 44)
private val BlueBadge = Color(0, 182, 243)
private val BlueBadgeBg = Color(0, 31, 42)
private val GridIconBg = Color(18, 39, 46)
private val AmberIcon = Color(225, 124, 0)
private val AmberIconBg = Color(43, 28, 8)
private val AmberBadge = Color(232, 131, 0)
private val AmberBadgeBg = Color(41, 22, 0)
private val PurpleIcon = Color(182, 113, 249)
private val PurpleIconBg = Color(36, 26, 46)
private val PurpleBadge = Color(188, 119, 255)
private val PurpleBadgeBg = Color(32, 20, 45)

/** Icon + badge colors for a display type's picker row. */
private data class TypeColors(val icon: Color, val iconBg: Color, val badge: Color, val badgeBg: Color)

private fun typeColorsFor(type: GoLiveDisplayType): TypeColors = when (type) {
    GoLiveDisplayType.FULLSCREEN -> TypeColors(BlueIcon, BlueIconBg, BlueBadge, BlueBadgeBg)
    GoLiveDisplayType.LOWER_THIRD -> TypeColors(AmberIcon, AmberIconBg, AmberBadge, AmberBadgeBg)
    GoLiveDisplayType.STAGE_MONITOR -> TypeColors(PurpleIcon, PurpleIconBg, PurpleBadge, PurpleBadgeBg)
}

/** Which visual family an output belongs to, driving the picker row's icon and badge colors. */
enum class GoLiveDisplayType { FULLSCREEN, LOWER_THIRD, STAGE_MONITOR }

/** One selectable output in the Go Live display picker. */
data class GoLiveDisplayOption(
    val index: Int,
    val number: Int,
    val type: GoLiveDisplayType,
    val typeLabel: String,
    val sub: String,
    val enabled: Boolean,
)

/**
 * Builds the list of selectable outputs for the Go Live display picker from the current projection
 * settings, connected screens/DeckLink devices and dev-fallback windows.
 */
@Composable
fun rememberGoLiveDisplays(
    appSettings: AppSettings,
    isEnabled: (ScreenAssignment) -> Boolean,
): List<GoLiveDisplayOption> {
    val proj = appSettings.projectionSettings
    val screens = rememberScreenDevices()
    val primary = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice }
    val deckLinkCount = remember { if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0 }
    val realWindowCount = screens.count { it != primary } + deckLinkCount
    val devFallback = (!BuildConfig.IS_RELEASE || DevFlags.forceDevWindow) && realWindowCount == 0
    val count = realWindowCount + if (devFallback) proj.devWindowCount.coerceAtLeast(1) else 0

    val fsLabel = stringResource(Res.string.display_fullscreen)
    val ltLabel = stringResource(Res.string.projection_type_lower_third)
    val smLabel = stringResource(Res.string.display_stage_monitor)
    val simulated = stringResource(Res.string.projection_simulated_output)

    return (0 until count).map { i ->
        val a = proj.getAssignment(i)
        val type = when {
            a.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR -> GoLiveDisplayType.STAGE_MONITOR
            a.isLowerThird -> GoLiveDisplayType.LOWER_THIRD
            else -> GoLiveDisplayType.FULLSCREEN
        }
        val typeLabel = when (type) {
            GoLiveDisplayType.FULLSCREEN -> fsLabel
            GoLiveDisplayType.LOWER_THIRD -> ltLabel
            GoLiveDisplayType.STAGE_MONITOR -> smLabel
        }
        val isDev = devFallback && i >= realWindowCount
        val sub = when {
            isDev -> simulated
            a.targetBoundsW > 0 -> "${a.targetBoundsW}×${a.targetBoundsH}"
            else -> ""
        }
        GoLiveDisplayOption(
            index = i,
            number = i + 1,
            type = type,
            typeLabel = typeLabel,
            sub = sub,
            enabled = isEnabled(a),
        )
    }
}

/**
 * Convenience wrapper around [ProjectionGoLiveButton] for a content tab. Reads the shared Go Live
 * target and live state from [presenterManager], builds the display list gated by [isEnabled], and
 * runs the tab's own [onGoLive] action (the chosen target is stored automatically). [liveMode] is
 * the [Presenting] mode this tab represents, used to light up the "Live" state.
 */
@Composable
fun TabGoLiveButton(
    appSettings: AppSettings?,
    presenterManager: PresenterManager?,
    liveMode: Presenting,
    isEnabled: (ScreenAssignment) -> Boolean,
    onGoLive: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val displays = rememberGoLiveDisplays(appSettings ?: AppSettings(), isEnabled)
    val target = presenterManager?.goLiveTarget?.value ?: GoLiveTarget.All
    val isLive = presenterManager?.presentingMode?.value == liveMode
    ProjectionGoLiveButton(
        isLive = isLive,
        selectedTarget = target,
        displays = displays,
        onGoLive = { chosen ->
            presenterManager?.setGoLiveTarget(chosen)
            onGoLive()
        },
        onSelectTarget = { presenterManager?.setGoLiveTarget(it) },
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * Split "Go Live" button: the main area fires [onGoLive] for the currently selected target, while
 * the trailing trigger opens a dropdown to pick which output (or all outputs) to target.
 */
@Composable
fun ProjectionGoLiveButton(
    isLive: Boolean,
    selectedTarget: GoLiveTarget,
    displays: List<GoLiveDisplayOption>,
    onGoLive: (GoLiveTarget) -> Unit,
    onSelectTarget: (GoLiveTarget) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }

    val selDisp = (selectedTarget as? GoLiveTarget.Display)?.let { t -> displays.find { it.index == t.index } }
    // Single-line compact label for the trigger (the row height is too short for two lines):
    // "ALL" for all outputs, "#N" for a specific one.
    val compactLabel = if (selDisp == null) stringResource(Res.string.projection_target_all_short)
        else stringResource(Res.string.projection_display_hash, selDisp.number)

    val mainBg = if (isLive) LiveMainBg else IdleMainBg
    val mainText = if (isLive) Color.White else IdleMainText
    val dot = if (isLive) Color.White else IdleDot
    val typeColor = if (isLive) LiveTypeColor else IdleTypeColor
    val divider = if (isLive) LiveDivider else IdleDivider

    Box(modifier) {
        Row(
            Modifier
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .alpha(if (enabled) 1f else 0.45f)
        ) {
            // Main "Go Live" area — fixed width so it never resizes between "Go Live"/"Live".
            Row(
                Modifier
                    .fillMaxHeight()
                    .width(74.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(mainBg)
                    .clickable(enabled = enabled) { onGoLive(selectedTarget) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Text(
                    if (isLive) stringResource(Res.string.projection_live_label)
                    else stringResource(Res.string.projection_go_live_label),
                    color = mainText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(divider))

            // Trigger area (opens the picker) — single line so it fits the 34dp row height.
            Row(
                Modifier
                    .fillMaxHeight()
                    .width(68.dp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(mainBg)
                    .clickable(enabled = enabled) { open = !open }
                    .padding(start = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    compactLabel,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = typeColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(14.dp).rotate(if (open) 180f else 0f)
                )
            }
        }

        if (open) {
            val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val x = (anchorBounds.right - popupContentSize.width)
                            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                        val y = anchorBounds.bottom + gapPx
                        return IntOffset(x, y)
                    }
                },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    Modifier
                        .width(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DropBg)
                        .border(1.dp, DropBorder, RoundedCornerShape(12.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)
                    ) {
                        Text(
                            stringResource(Res.string.projection_output_display_header).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = HeaderText
                        )
                    }
                    HorizontalDivider(color = HeaderBorder)
                    displays.forEach { d ->
                        DisplayPickerRow(
                            option = d,
                            selected = selectedTarget is GoLiveTarget.Display &&
                                (selectedTarget as GoLiveTarget.Display).index == d.index,
                            onClick = {
                                onSelectTarget(GoLiveTarget.Display(d.index))
                                open = false
                            }
                        )
                    }
                    HorizontalDivider(color = HeaderBorder)
                    AllDisplaysRow(
                        selected = selectedTarget is GoLiveTarget.All,
                        onClick = {
                            onSelectTarget(GoLiveTarget.All)
                            open = false
                        }
                    )
                }
            }
        }
    }
}

/** A single output row in the picker dropdown. */
@Composable
private fun DisplayPickerRow(
    option: GoLiveDisplayOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tc = typeColorsFor(option.type)
    val rowBg = when {
        selected -> RowSelected
        option.enabled && hovered -> RowHover
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (option.enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .hoverable(interaction, enabled = option.enabled)
            .background(rowBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .alpha(if (option.enabled) 1f else 0.65f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(tc.iconBg),
            contentAlignment = Alignment.Center
        ) {
            MonitorGlyph(tc.icon, option.type)
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(Res.string.projection_display_num, option.number),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) TextSelected else TextUnselected,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    option.typeLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tc.badge,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tc.badgeBg)
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
            // Disabled outputs show "(disabled)" on the sub line instead of the resolution/simulated label.
            val subText = if (!option.enabled) stringResource(Res.string.projection_go_live_disabled) else option.sub
            if (subText.isNotEmpty()) {
                Text(
                    subText,
                    fontSize = 11.sp,
                    fontWeight = if (!option.enabled) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (!option.enabled) DisabledLabel else if (selected) SubSelected else SubUnselected,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = CheckGreen,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** The "All displays" row at the bottom of the picker dropdown. */
@Composable
private fun AllDisplaysRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rowBg = when {
        selected -> RowSelected
        hovered -> RowHover
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .hoverable(interaction)
            .background(rowBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(GridIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = null,
                tint = BlueIcon,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.projection_all_displays),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) TextSelected else TextUnselected
            )
            Text(
                stringResource(Res.string.projection_all_displays_sub),
                fontSize = 11.sp,
                color = if (selected) SubSelected else SubUnselected
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = CheckGreen,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** A tiny monitor icon drawn to fit a picker row's colored icon box. */
@Composable
private fun MonitorGlyph(color: Color, type: GoLiveDisplayType) {
    Canvas(Modifier.size(18.dp, 14.dp)) {
        val strokeW = size.minDimension * 0.11f
        val inset = strokeW
        val screenBottom = size.height * 0.74f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, screenBottom - inset),
            style = Stroke(width = strokeW),
            cornerRadius = CornerRadius(strokeW * 1.5f, strokeW * 1.5f)
        )
        // Stand line at the bottom center
        val standHalf = size.width * 0.18f
        drawLine(
            color = color,
            start = Offset(size.width / 2f - standHalf, size.height - strokeW),
            end = Offset(size.width / 2f + standHalf, size.height - strokeW),
            strokeWidth = strokeW
        )
        if (type == GoLiveDisplayType.STAGE_MONITOR) {
            drawCircle(
                color = color,
                radius = size.minDimension * 0.14f,
                center = Offset(size.width / 2f, (inset + screenBottom) / 2f),
                style = Stroke(width = strokeW)
            )
        }
    }
}
