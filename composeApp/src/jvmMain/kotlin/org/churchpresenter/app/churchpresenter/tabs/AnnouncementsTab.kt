package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.announcement_text
import churchpresenter.composeapp.generated.resources.announcement_text_hint
import churchpresenter.composeapp.generated.resources.anim_slide_from_bottom
import churchpresenter.composeapp.generated.resources.anim_slide_from_left
import churchpresenter.composeapp.generated.resources.anim_slide_from_right
import churchpresenter.composeapp.generated.resources.anim_slide_from_top
import churchpresenter.composeapp.generated.resources.anim_slide_along_top_ltr
import churchpresenter.composeapp.generated.resources.anim_slide_along_top_rtl
import churchpresenter.composeapp.generated.resources.anim_slide_along_bottom_ltr
import churchpresenter.composeapp.generated.resources.anim_slide_along_bottom_rtl
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.TopStart
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.announcement_animation
import churchpresenter.composeapp.generated.resources.announcement_animation_duration
import churchpresenter.composeapp.generated.resources.announcement_background_color_label
import churchpresenter.composeapp.generated.resources.bottom_center
import churchpresenter.composeapp.generated.resources.bottom_left
import churchpresenter.composeapp.generated.resources.bottom_right
import churchpresenter.composeapp.generated.resources.center
import churchpresenter.composeapp.generated.resources.center_left
import churchpresenter.composeapp.generated.resources.center_right
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.position_on_screen
import churchpresenter.composeapp.generated.resources.text_color
import churchpresenter.composeapp.generated.resources.top_center
import churchpresenter.composeapp.generated.resources.top_left
import churchpresenter.composeapp.generated.resources.top_right
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.viewmodel.AnnouncementsViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

@Composable
fun AnnouncementsTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    presenterManager: PresenterManager? = null,
    onAddToSchedule: ((settings: org.churchpresenter.app.churchpresenter.data.AnnouncementsSettings) -> Unit)? = null
) {
    val viewModel = remember { AnnouncementsViewModel() }

    // Sync from settings on load / settings change
    LaunchedEffect(appSettings.announcementsSettings) {
        viewModel.syncFromSettings(appSettings.announcementsSettings)
    }

    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Text input ───────────────────────────────────────────────
        SectionLabel(stringResource(Res.string.announcement_text))
        OutlinedTextField(
            value = viewModel.text,
            onValueChange = {
                viewModel.setText(it)
                viewModel.saveToSettings(onSettingsChange)
            },
            placeholder = {
                Text(
                    text = stringResource(Res.string.announcement_text_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Font type + size + style + colors — all on one row ───────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Font type — fixed narrow width
            Column(modifier = Modifier.width(160.dp)) {
                SectionLabel(stringResource(Res.string.font_type))
                FontSettingsDropdown(
                    value = viewModel.fontType,
                    fonts = availableFonts,
                    onValueChange = {
                        viewModel.setFontType(it)
                        viewModel.saveToSettings(onSettingsChange)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Font size
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel(stringResource(Res.string.font_size))
                NumberSettingsTextField(
                    initialText = viewModel.fontSize,
                    range = 8..200,
                    onValueChange = {
                        viewModel.setFontSize(it)
                        viewModel.saveToSettings(onSettingsChange)
                    }
                )
            }
            // Style buttons (B/I/U/S)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel("")
                TextStyleButtons(
                    bold      = viewModel.bold,
                    italic    = viewModel.italic,
                    underline = viewModel.underline,
                    shadow    = viewModel.shadow,
                    onBoldChange      = { viewModel.setBold(it);      viewModel.saveToSettings(onSettingsChange) },
                    onItalicChange    = { viewModel.setItalic(it);    viewModel.saveToSettings(onSettingsChange) },
                    onUnderlineChange = { viewModel.setUnderline(it); viewModel.saveToSettings(onSettingsChange) },
                    onShadowChange    = { viewModel.setShadow(it);    viewModel.saveToSettings(onSettingsChange) }
                )
            }
            // Text color
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel(stringResource(Res.string.text_color))
                ColorPickerField(
                    color = viewModel.textColor,
                    onColorChange = {
                        viewModel.setTextColor(it)
                        viewModel.saveToSettings(onSettingsChange)
                    }
                )
            }
            // Background color
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel(stringResource(Res.string.announcement_background_color_label))
                ColorPickerField(
                    color = viewModel.backgroundColor,
                    onColorChange = {
                        viewModel.setBackgroundColor(it)
                        viewModel.saveToSettings(onSettingsChange)
                    }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Position on screen + preview ─────────────────────────────
        SectionLabel(stringResource(Res.string.position_on_screen))
        Spacer(Modifier.height(2.dp))

        val positions = listOf(
            Constants.TOP_LEFT      to stringResource(Res.string.top_left),
            Constants.TOP_CENTER    to stringResource(Res.string.top_center),
            Constants.TOP_RIGHT     to stringResource(Res.string.top_right),
            Constants.CENTER_LEFT   to stringResource(Res.string.center_left),
            Constants.CENTER        to stringResource(Res.string.center),
            Constants.CENTER_RIGHT  to stringResource(Res.string.center_right),
            Constants.BOTTOM_LEFT   to stringResource(Res.string.bottom_left),
            Constants.BOTTOM_CENTER to stringResource(Res.string.bottom_center),
            Constants.BOTTOM_RIGHT  to stringResource(Res.string.bottom_right)
        )

        val slideFromLeftText       = stringResource(Res.string.anim_slide_from_left)
        val slideFromRightText      = stringResource(Res.string.anim_slide_from_right)
        val slideFromTopText        = stringResource(Res.string.anim_slide_from_top)
        val slideFromBottomText     = stringResource(Res.string.anim_slide_from_bottom)
        val slideAlongTopLtrText    = stringResource(Res.string.anim_slide_along_top_ltr)
        val slideAlongTopRtlText    = stringResource(Res.string.anim_slide_along_top_rtl)
        val slideAlongBottomLtrText = stringResource(Res.string.anim_slide_along_bottom_ltr)
        val slideAlongBottomRtlText = stringResource(Res.string.anim_slide_along_bottom_rtl)
        val fadeText                = stringResource(Res.string.animation_fade)
        val noneText                = stringResource(Res.string.animation_none)

        val animItems = listOf(
            slideFromBottomText,
            slideFromTopText,
            slideFromLeftText,
            slideFromRightText,
            slideAlongBottomLtrText,
            slideAlongBottomRtlText,
            slideAlongTopLtrText,
            slideAlongTopRtlText,
            fadeText,
            noneText
        )
        val selectedAnim = when (viewModel.animationType) {
            Constants.ANIMATION_SLIDE_FROM_LEFT        -> slideFromLeftText
            Constants.ANIMATION_SLIDE_FROM_RIGHT       -> slideFromRightText
            Constants.ANIMATION_SLIDE_FROM_TOP         -> slideFromTopText
            Constants.ANIMATION_SLIDE_FROM_BOTTOM      -> slideFromBottomText
            Constants.ANIMATION_SLIDE_ALONG_TOP_LTR    -> slideAlongTopLtrText
            Constants.ANIMATION_SLIDE_ALONG_TOP_RTL    -> slideAlongTopRtlText
            Constants.ANIMATION_SLIDE_ALONG_BOTTOM_LTR -> slideAlongBottomLtrText
            Constants.ANIMATION_SLIDE_ALONG_BOTTOM_RTL -> slideAlongBottomRtlText
            Constants.ANIMATION_FADE                   -> fadeText
            else                                       -> noneText
        }
        val durationMs = viewModel.animationDuration

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left column: 3×3 grid + animation controls below it
            Column(
                modifier = Modifier.fillMaxWidth(0.34f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                positions.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        rowItems.forEach { (posConst, posLabel) ->
                            val isSelected = viewModel.position == posConst
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(2.2f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        ),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .clickable {
                                        viewModel.setPosition(posConst)
                                        viewModel.saveToSettings(onSettingsChange)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = posLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Animation dropdown
                SectionLabel(stringResource(Res.string.announcement_animation))
                AnnouncementAnimationDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    items = animItems,
                    selected = selectedAnim,
                    onSelectedChange = { sel ->
                        val key = when (sel) {
                            slideFromLeftText       -> Constants.ANIMATION_SLIDE_FROM_LEFT
                            slideFromRightText      -> Constants.ANIMATION_SLIDE_FROM_RIGHT
                            slideFromTopText        -> Constants.ANIMATION_SLIDE_FROM_TOP
                            slideFromBottomText     -> Constants.ANIMATION_SLIDE_FROM_BOTTOM
                            slideAlongTopLtrText    -> Constants.ANIMATION_SLIDE_ALONG_TOP_LTR
                            slideAlongTopRtlText    -> Constants.ANIMATION_SLIDE_ALONG_TOP_RTL
                            slideAlongBottomLtrText -> Constants.ANIMATION_SLIDE_ALONG_BOTTOM_LTR
                            slideAlongBottomRtlText -> Constants.ANIMATION_SLIDE_ALONG_BOTTOM_RTL
                            fadeText                -> Constants.ANIMATION_FADE
                            else                    -> Constants.ANIMATION_NONE
                        }
                        viewModel.setAnimationType(key)
                        viewModel.saveToSettings(onSettingsChange)
                    }
                )

                Spacer(Modifier.height(4.dp))

                // Duration slider — 500ms increments, max 30s
                val maxDuration = 30000
                val stepSize   = 500
                SectionLabel("${stringResource(Res.string.announcement_animation_duration)}: ${durationMs}ms")
                androidx.compose.material3.Slider(
                    value = durationMs.toFloat(),
                    onValueChange = { v ->
                        val snapped = (v / stepSize.toFloat()).toInt() * stepSize
                        viewModel.setAnimationDuration(snapped.coerceAtLeast(stepSize))
                        viewModel.saveToSettings(onSettingsChange)
                    },
                    valueRange = stepSize.toFloat()..maxDuration.toFloat(),
                    steps = (maxDuration / stepSize) - 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Preview — shows text scrolling from edge, ignores position for directional anims
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        RoundedCornerShape(6.dp)
                    )
            ) {
                // Scale factor: preview width vs a reference full-screen width of 1920px
                val scaleFactor = maxWidth / 1920.dp
                val scaledFontSize = (viewModel.fontSize * scaleFactor).coerceAtLeast(6f).sp
                val scaledPadH = (32 * scaleFactor).coerceAtLeast(2f).dp
                val scaledPadV = (16 * scaleFactor).coerceAtLeast(1f).dp
                val isDirectional = viewModel.animationType in listOf(
                    Constants.ANIMATION_SLIDE_FROM_LEFT,
                    Constants.ANIMATION_SLIDE_FROM_RIGHT,
                    Constants.ANIMATION_SLIDE_FROM_TOP,
                    Constants.ANIMATION_SLIDE_FROM_BOTTOM,
                    Constants.ANIMATION_SLIDE_ALONG_TOP_LTR,
                    Constants.ANIMATION_SLIDE_ALONG_TOP_RTL,
                    Constants.ANIMATION_SLIDE_ALONG_BOTTOM_LTR,
                    Constants.ANIMATION_SLIDE_ALONG_BOTTOM_RTL
                )

                val isHorizontal = viewModel.animationType != Constants.ANIMATION_SLIDE_FROM_TOP &&
                                   viewModel.animationType != Constants.ANIMATION_SLIDE_FROM_BOTTOM
                val movesPositive = viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                                   viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_TOP ||
                                   viewModel.animationType == Constants.ANIMATION_SLIDE_ALONG_TOP_LTR ||
                                   viewModel.animationType == Constants.ANIMATION_SLIDE_ALONG_BOTTOM_LTR

                val verticalAnchor = when (viewModel.animationType) {
                    Constants.ANIMATION_SLIDE_ALONG_TOP_LTR,
                    Constants.ANIMATION_SLIDE_ALONG_TOP_RTL    -> Alignment.TopCenter
                    Constants.ANIMATION_SLIDE_ALONG_BOTTOM_LTR,
                    Constants.ANIMATION_SLIDE_ALONG_BOTTOM_RTL -> Alignment.BottomCenter
                    else                                        -> Alignment.Center
                }

                val scrollDurationMs = durationMs.coerceAtLeast(500)

                // key() restarts the infinite transition whenever duration or direction changes
                key(scrollDurationMs, movesPositive) {
                    val infiniteTransition = rememberInfiniteTransition(label = "scroll")
                    val offsetFraction by infiniteTransition.animateFloat(
                        initialValue = if (movesPositive) -1f else 1f,
                        targetValue  = if (movesPositive) 1f else -1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = scrollDurationMs, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "offsetFraction"
                    )

                    if (isDirectional) {
                        val textComposable: @Composable () -> Unit = {
                            Text(
                                text = viewModel.text.ifBlank { "Preview" },
                                fontSize = scaledFontSize,
                                color = Utils.parseHexColor(viewModel.textColor),
                                maxLines = if (isHorizontal) 1 else 3,
                                modifier = Modifier
                                    .background(Utils.parseHexColor(viewModel.backgroundColor), RoundedCornerShape(2.dp))
                                    .padding(horizontal = scaledPadH, vertical = scaledPadV)
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isHorizontal) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(verticalAnchor)
                                        .padding(vertical = 4.dp)
                                        .graphicsLayer { translationX = size.width * offsetFraction },
                                    contentAlignment = Alignment.Center
                                ) { textComposable() }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .align(Alignment.Center)
                                        .graphicsLayer { translationY = size.height * offsetFraction },
                                    contentAlignment = Alignment.Center
                                ) { textComposable() }
                            }
                        }
                    } else {
                        // FADE or NONE — respect position setting
                        val previewAlignment = when (viewModel.position) {
                            Constants.TOP_LEFT      -> Alignment.TopStart
                            Constants.TOP_CENTER    -> Alignment.TopCenter
                            Constants.TOP_RIGHT     -> Alignment.TopEnd
                            Constants.CENTER_LEFT   -> Alignment.CenterStart
                            Constants.CENTER        -> Alignment.Center
                            Constants.CENTER_RIGHT  -> Alignment.CenterEnd
                            Constants.BOTTOM_LEFT   -> Alignment.BottomStart
                            Constants.BOTTOM_CENTER -> Alignment.BottomCenter
                            Constants.BOTTOM_RIGHT  -> Alignment.BottomEnd
                            else                    -> Alignment.Center
                        }
                        val previewDuration = durationMs.coerceAtLeast(50)
                        val previewKey = Triple(viewModel.text, viewModel.animationType, viewModel.position)
                        AnimatedContent(
                            targetState = previewKey,
                            transitionSpec = {
                                if (viewModel.animationType == Constants.ANIMATION_FADE)
                                    fadeIn(tween(previewDuration)) togetherWith fadeOut(tween(previewDuration))
                                else
                                    EnterTransition.None togetherWith ExitTransition.None
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "AnnouncementPreview"
                        ) { (text, _, _) ->
                            Box(
                                modifier = Modifier.fillMaxSize().padding(scaledPadH),
                                contentAlignment = previewAlignment
                            ) {
                                Text(
                                    text = text.ifBlank { "Preview" },
                                    fontSize = scaledFontSize,
                                    color = Utils.parseHexColor(viewModel.textColor),
                                    maxLines = 3,
                                    modifier = Modifier
                                        .background(
                                            Utils.parseHexColor(viewModel.backgroundColor),
                                            RoundedCornerShape(2.dp)
                                        )
                                        .padding(horizontal = scaledPadH, vertical = scaledPadV)
                                )
                            }
                        }
                    }
                }
            }
        }


        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onAddToSchedule != null) {
                Button(
                    onClick = { onAddToSchedule.invoke(viewModel.buildSettings()) },
                    enabled = viewModel.text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.add_to_schedule),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (presenterManager != null) {
                Button(
                    onClick = { viewModel.goLive(presenterManager) },
                    enabled = viewModel.text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.go_live),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementAnimationDropdown(
    modifier: Modifier = Modifier,
    items: List<String>,
    selected: String,
    onSelectedChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.wrapContentSize(TopStart)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = selected,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "▾",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item == selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelectedChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
