package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.anim_slide_from_bottom
import churchpresenter.composeapp.generated.resources.anim_slide_from_left
import churchpresenter.composeapp.generated.resources.anim_slide_from_right
import churchpresenter.composeapp.generated.resources.anim_slide_from_top
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.announcement_animation
import churchpresenter.composeapp.generated.resources.announcement_animation_speed
import churchpresenter.composeapp.generated.resources.announcement_loop_count
import churchpresenter.composeapp.generated.resources.announcement_loop_tooltip
import churchpresenter.composeapp.generated.resources.preview
import churchpresenter.composeapp.generated.resources.time_separator
import churchpresenter.composeapp.generated.resources.announcement_background_color_label
import churchpresenter.composeapp.generated.resources.announcement_text
import churchpresenter.composeapp.generated.resources.transparent_default
import churchpresenter.composeapp.generated.resources.announcement_text_hint
import churchpresenter.composeapp.generated.resources.bottom_center
import churchpresenter.composeapp.generated.resources.bottom_left
import churchpresenter.composeapp.generated.resources.bottom_right
import churchpresenter.composeapp.generated.resources.center
import churchpresenter.composeapp.generated.resources.center_left
import churchpresenter.composeapp.generated.resources.center_right
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.position_on_screen
import churchpresenter.composeapp.generated.resources.text_color
import churchpresenter.composeapp.generated.resources.timer_expired
import churchpresenter.composeapp.generated.resources.timer_expired_text_hint
import churchpresenter.composeapp.generated.resources.timer_expired_text_label
import churchpresenter.composeapp.generated.resources.timer_minutes
import churchpresenter.composeapp.generated.resources.timer_pause
import churchpresenter.composeapp.generated.resources.timer_reset
import churchpresenter.composeapp.generated.resources.timer_hours
import churchpresenter.composeapp.generated.resources.timer_seconds
import churchpresenter.composeapp.generated.resources.timer_start
import churchpresenter.composeapp.generated.resources.timer_title
import churchpresenter.composeapp.generated.resources.top_center
import churchpresenter.composeapp.generated.resources.top_left
import churchpresenter.composeapp.generated.resources.top_right
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.viewmodel.AnnouncementsViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnnouncementsTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    presenterManager: PresenterManager? = null,
    onAddToSchedule: ((settings: AnnouncementsSettings) -> Unit)? = null
) {
    val viewModel = remember { AnnouncementsViewModel() }

    DisposableEffect(Unit) { onDispose { viewModel.dispose() } }

    // Sync from settings on load / settings change
    LaunchedEffect(appSettings.announcementsSettings) {
        viewModel.syncFromSettings(appSettings.announcementsSettings)
    }

    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    val timerExpiredLabel = stringResource(Res.string.timer_expired)
    val startLabel        = stringResource(Res.string.timer_start)
    val pauseLabel        = stringResource(Res.string.timer_pause)
    val resetLabel        = stringResource(Res.string.timer_reset)
    val hrLabel           = stringResource(Res.string.timer_hours)
    val minLabel          = stringResource(Res.string.timer_minutes)
    val secLabel          = stringResource(Res.string.timer_seconds)

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // Build all the data needed for both columns up front
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
            val fadeText                = stringResource(Res.string.animation_fade)
            val noneText                = stringResource(Res.string.animation_none)
            val animItems = listOf(
                slideFromBottomText, slideFromTopText,
                slideFromLeftText, slideFromRightText,
                fadeText, noneText
            )
            val selectedAnim = when (viewModel.animationType) {
                Constants.ANIMATION_SLIDE_FROM_LEFT        -> slideFromLeftText
                Constants.ANIMATION_SLIDE_FROM_RIGHT       -> slideFromRightText
                Constants.ANIMATION_SLIDE_FROM_TOP         -> slideFromTopText
                Constants.ANIMATION_SLIDE_FROM_BOTTOM      -> slideFromBottomText
                Constants.ANIMATION_FADE                   -> fadeText
                else                                       -> noneText
            }
            val durationMs = viewModel.animationDuration

            // ── Two-column layout ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // ── LEFT COLUMN: text + font + position + animation + duration ─
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Announcement text + buttons
                    SectionLabel(stringResource(Res.string.announcement_text))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val announcementIsNarrow = maxWidth < 500.dp

                        val textField: @Composable (Modifier) -> Unit = { mod ->
                            OutlinedTextField(
                                value = viewModel.text,
                                onValueChange = {
                                    viewModel.setText(it)
                                    viewModel.saveToSettings(onSettingsChange)
                                },
                                placeholder = {
                                    Text(
                                        text = stringResource(Res.string.announcement_text_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                },
                                modifier = mod,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = false,
                                maxLines = 3
                            )
                        }

                        val buttons: @Composable () -> Unit = {
                            if (presenterManager != null) {
                                Button(
                                    onClick = { viewModel.goLive(presenterManager, onSettingsChange) },
                                    enabled = viewModel.text.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(Res.string.go_live), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (onAddToSchedule != null) {
                                Button(
                                    onClick = {
                                        onAddToSchedule.invoke(
                                            viewModel.buildSettings().copy(
                                                timerMinutes = 0,
                                                timerSeconds = 0,
                                                timerTextColor = "#FFFFFF",
                                                timerExpiredText = ""
                                            )
                                        )
                                    },
                                    enabled = viewModel.text.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(Res.string.add_to_schedule), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (announcementIsNarrow) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                textField(Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    buttons()
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                textField(Modifier.weight(1f))
                                buttons()
                            }
                        }
                    }

                    // Color + style row (matches Bible settings layout)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorPickerField(
                                color = viewModel.textColor,
                                onColorChange = {
                                    viewModel.setTextColor(it)
                                    viewModel.saveToSettings(onSettingsChange)
                                }
                            )
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
                            HorizontalAlignmentButtons(
                                selectedAlignment = viewModel.horizontalAlignment,
                                onAlignmentChange = {
                                    viewModel.setHorizontalAlignment(it)
                                    viewModel.saveToSettings(onSettingsChange)
                                },
                                leftValue = Constants.LEFT,
                                centerValue = Constants.CENTER,
                                rightValue = Constants.RIGHT
                            )
                        }
                        AnimatedVisibility(visible = viewModel.shadow) {
                            ShadowDetailRow(
                                shadowColor = appSettings.announcementsSettings.shadowColor,
                                shadowSize = appSettings.announcementsSettings.shadowSize,
                                shadowOpacity = appSettings.announcementsSettings.shadowOpacity,
                                onColorChange = { c -> onSettingsChange { s -> s.copy(announcementsSettings = s.announcementsSettings.copy(shadowColor = c)) } },
                                onSizeChange = { v -> onSettingsChange { s -> s.copy(announcementsSettings = s.announcementsSettings.copy(shadowSize = v)) } },
                                onOpacityChange = { v -> onSettingsChange { s -> s.copy(announcementsSettings = s.announcementsSettings.copy(shadowOpacity = v)) } },
                            )
                        }
                    }

                    // Font type + size
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.width(140.dp)) {
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
                    }

                    // Background color
                    Column(horizontalAlignment = Alignment.Start) {
                        SectionLabel(stringResource(Res.string.announcement_background_color_label))
                            if (viewModel.backgroundColor == "transparent") {
                                Button(
                                    onClick = {
                                        viewModel.setBackgroundColor("#000000")
                                        viewModel.saveToSettings(onSettingsChange)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(
                                        stringResource(Res.string.transparent_default),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            } else {
                                ColorPickerField(
                                    color = viewModel.backgroundColor,
                                    onColorChange = {
                                        viewModel.setBackgroundColor(it)
                                        viewModel.saveToSettings(onSettingsChange)
                                    }
                                )
                                Button(
                                    onClick = {
                                        viewModel.setBackgroundColor("transparent")
                                        viewModel.saveToSettings(onSettingsChange)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(
                                        stringResource(Res.string.transparent_default),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Position on screen
                    SectionLabel(stringResource(Res.string.position_on_screen))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.widthIn(max = 400.dp)) {
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
                                            .aspectRatio(3f)
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
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                } // end left column

                // ── RIGHT COLUMN: preview (50%) ───────────────────────
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SectionLabel(stringResource(Res.string.preview))
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(presenterAspectRatio())
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.ui.graphics.Color.Black)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                RoundedCornerShape(4.dp)
                            )
                    ) {
                        val scaleFactor = maxWidth / presenterScreenBounds().width.dp
                        val scaledFontSize = (viewModel.fontSize * scaleFactor).coerceAtLeast(4f).sp
                        val scaledPadH = (32 * scaleFactor).coerceAtLeast(1f).dp
                        val scaledPadV = (16 * scaleFactor).coerceAtLeast(1f).dp
                        val isDirectional = viewModel.animationType in listOf(
                            Constants.ANIMATION_SLIDE_FROM_LEFT,
                            Constants.ANIMATION_SLIDE_FROM_RIGHT,
                            Constants.ANIMATION_SLIDE_FROM_TOP,
                            Constants.ANIMATION_SLIDE_FROM_BOTTOM
                        )
                        val isHorizontal = viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                                           viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_RIGHT
                        val movesPositive = viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                                           viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_TOP
                        // For horizontal slides: use position's vertical component
                        // For vertical slides: use position's horizontal component
                        val slideAlignment: Alignment = if (isHorizontal) {
                            when {
                                viewModel.position.startsWith("Top")    -> Alignment.TopCenter
                                viewModel.position.startsWith("Bottom") -> Alignment.BottomCenter
                                else                                    -> Alignment.Center
                            }
                        } else {
                            when {
                                viewModel.position.endsWith("Left")  -> Alignment.CenterStart
                                viewModel.position.endsWith("Right") -> Alignment.CenterEnd
                                else                                 -> Alignment.Center
                            }
                        }
                        val scrollDurationMs = durationMs.coerceAtLeast(500)
                        val previewTextAlign = when (viewModel.horizontalAlignment) {
                            Constants.LEFT -> androidx.compose.ui.text.style.TextAlign.Left
                            Constants.RIGHT -> androidx.compose.ui.text.style.TextAlign.Right
                            else -> androidx.compose.ui.text.style.TextAlign.Center
                        }
                        key(scrollDurationMs, movesPositive) {
                            val infiniteTransition = rememberInfiniteTransition(label = "previewScroll")
                            val offsetFraction by infiniteTransition.animateFloat(
                                initialValue = if (movesPositive) -1f else 1f,
                                targetValue  = if (movesPositive) 1f else -1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = scrollDurationMs, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "previewOffset"
                            )
                            if (isDirectional) {
                                val textComposable: @Composable () -> Unit = {
                                    Box(
                                        modifier = Modifier
                                            .then(if (isHorizontal) Modifier.wrapContentWidth(unbounded = true) else Modifier)
                                            .wrapContentHeight()
                                            .background(
                                                if (viewModel.backgroundColor == "transparent") Color.Transparent
                                                else Utils.parseHexColor(viewModel.backgroundColor),
                                                RoundedCornerShape(2.dp)
                                            )
                                            .padding(horizontal = scaledPadH, vertical = scaledPadV),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = viewModel.text.ifBlank { stringResource(Res.string.preview) },
                                            fontSize = scaledFontSize,
                                            color = Utils.parseHexColor(viewModel.textColor),
                                            textAlign = previewTextAlign,
                                            softWrap = !isHorizontal,
                                        )
                                    }
                                }
                                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                                    if (isHorizontal) {
                                        Box(
                                            modifier = Modifier
                                                .align(slideAlignment)
                                                .graphicsLayer { translationX = size.width * offsetFraction },
                                            contentAlignment = Alignment.Center
                                        ) { textComposable() }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .align(slideAlignment)
                                                .graphicsLayer { translationY = size.height * offsetFraction },
                                            contentAlignment = Alignment.Center
                                        ) { textComposable() }
                                    }
                                }
                            } else {
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
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = previewAlignment
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .wrapContentHeight()
                                                .background(
                                                    if (viewModel.backgroundColor == "transparent") Color.Transparent
                                                    else Utils.parseHexColor(viewModel.backgroundColor),
                                                    RoundedCornerShape(2.dp)
                                                )
                                                .padding(horizontal = scaledPadH, vertical = scaledPadV),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = text.ifBlank { stringResource(Res.string.preview) },
                                                fontSize = scaledFontSize,
                                                color = Utils.parseHexColor(viewModel.textColor),
                                                textAlign = previewTextAlign,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } // end BoxWithConstraints

                    // Animation + loop count on same line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                                        fadeText                -> Constants.ANIMATION_FADE
                                        else                    -> Constants.ANIMATION_NONE
                                    }
                                    viewModel.setAnimationType(key)
                                    viewModel.saveToSettings(onSettingsChange)
                                }
                            )
                        }
                        TooltipArea(
                            tooltip = {
                                Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(
                                        stringResource(Res.string.announcement_loop_tooltip),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                SectionLabel(stringResource(Res.string.announcement_loop_count))
                                NumberSettingsTextField(
                                    initialText = viewModel.loopCount,
                                    range = 0..99,
                                    onValueChange = { v ->
                                        viewModel.setLoopCount(v)
                                        viewModel.saveToSettings(onSettingsChange)
                                    }
                                )
                            }
                        }
                    }
                    val sliderMin = 500f
                    val sliderMax = 30000f
                    val sliderSum = sliderMin + sliderMax
                    SectionLabel("${stringResource(Res.string.announcement_animation_speed)}: ${"%.2f".format((sliderSum - durationMs) / 1000f)}")
                    androidx.compose.material3.Slider(
                        value = (sliderSum - durationMs.toFloat()),
                        onValueChange = { v ->
                            val dur = (sliderSum - v)
                            val snapped = (dur / sliderMin).toInt() * sliderMin.toInt()
                            viewModel.setAnimationDuration(snapped.coerceIn(sliderMin.toInt(), sliderMax.toInt()))
                            viewModel.saveToSettings(onSettingsChange)
                        },
                        valueRange = sliderMin..sliderMax,
                        steps = 58,
                        modifier = Modifier.fillMaxWidth()
                    )
                } // end right column
            } // end two-column Row

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── TIMER section (full width) ─────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(Res.string.timer_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val timerIsNarrow = maxWidth < 600.dp

                    val timerControls: @Composable (Modifier) -> Unit = { mod ->
                    Column(
                        modifier = mod,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Countdown display — full width, centered above steppers
                        Text(
                            text = AnnouncementsViewModel.formatTimer(viewModel.timerRemaining),
                            style = MaterialTheme.typography.displayMedium,
                            color = if (viewModel.timerExpired) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        // Steppers row — hours : minutes : seconds, centered
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hours stepper
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TimerStepButton("-") { viewModel.stepTimerHours(-1); viewModel.saveToSettings(onSettingsChange) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var hrText by remember { mutableStateOf("%02d".format(viewModel.timerHours)) }
                                    LaunchedEffect(viewModel.timerHours) { hrText = "%02d".format(viewModel.timerHours) }
                                    OutlinedTextField(
                                        value = hrText,
                                        onValueChange = { v ->
                                            val digits = v.filter { it.isDigit() }.take(2)
                                            hrText = digits
                                            digits.toIntOrNull()?.let { viewModel.setTimerHours(it); viewModel.saveToSettings(onSettingsChange) }
                                        },
                                        modifier = Modifier.width(64.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    )
                                    Text(hrLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TimerStepButton("+") { viewModel.stepTimerHours(1); viewModel.saveToSettings(onSettingsChange) }
                            }

                            Text(
                                text = stringResource(Res.string.time_separator),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            // Minutes stepper
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TimerStepButton("-") { viewModel.stepTimerMinutes(-1); viewModel.saveToSettings(onSettingsChange) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var minText by remember { mutableStateOf("%02d".format(viewModel.timerMinutes)) }
                                    LaunchedEffect(viewModel.timerMinutes) { minText = "%02d".format(viewModel.timerMinutes) }
                                    OutlinedTextField(
                                        value = minText,
                                        onValueChange = { v ->
                                            val digits = v.filter { it.isDigit() }.take(2)
                                            minText = digits
                                            digits.toIntOrNull()?.let { viewModel.setTimerMinutes(it); viewModel.saveToSettings(onSettingsChange) }
                                        },
                                        modifier = Modifier.width(64.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    )
                                    Text(minLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TimerStepButton("+") { viewModel.stepTimerMinutes(1); viewModel.saveToSettings(onSettingsChange) }
                            }

                            Text(
                                text = stringResource(Res.string.time_separator),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            // Seconds stepper
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TimerStepButton("-") { viewModel.stepTimerSeconds(-1); viewModel.saveToSettings(onSettingsChange) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var secText by remember { mutableStateOf("%02d".format(viewModel.timerSeconds)) }
                                    LaunchedEffect(viewModel.timerSeconds) { secText = "%02d".format(viewModel.timerSeconds) }
                                    OutlinedTextField(
                                        value = secText,
                                        onValueChange = { v ->
                                            val digits = v.filter { it.isDigit() }.take(2)
                                            secText = digits
                                            digits.toIntOrNull()?.let { viewModel.setTimerSeconds(it.coerceIn(0, 59)); viewModel.saveToSettings(onSettingsChange) }
                                        },
                                        modifier = Modifier.width(64.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    )
                                    Text(secLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TimerStepButton("+") { viewModel.stepTimerSeconds(1); viewModel.saveToSettings(onSettingsChange) }
                            }
                        }

                        // Controls row
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            val total = viewModel.timerHours * 3600 + viewModel.timerMinutes * 60 + viewModel.timerSeconds
                            IconButton(
                                onClick = {
                                    viewModel.saveToSettings(onSettingsChange)
                                    viewModel.startPauseTimer(
                                        onTick = { remaining ->
                                            if (presenterManager != null &&
                                                presenterManager.presentingMode.value == Presenting.ANNOUNCEMENTS) {
                                                presenterManager.setAnnouncementText(AnnouncementsViewModel.formatTimer(remaining))
                                            }
                                        },
                                        onExpired = { expiredMsg ->
                                            if (presenterManager != null) {
                                                presenterManager.setAnnouncementText(expiredMsg.ifBlank { timerExpiredLabel })
                                                presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
                                            }
                                        }
                                    )
                                },
                                enabled = total > 0 || viewModel.timerRunning,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (viewModel.timerRunning) MaterialTheme.colorScheme.secondaryContainer
                                                     else MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Icon(
                                    painter = painterResource(if (viewModel.timerRunning) Res.drawable.ic_pause else Res.drawable.ic_play),
                                    contentDescription = if (viewModel.timerRunning) pauseLabel else startLabel,
                                    tint = if (viewModel.timerRunning) MaterialTheme.colorScheme.onSecondaryContainer
                                           else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Button(
                                onClick = { viewModel.resetTimer() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(resetLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (presenterManager != null) {
                                Button(
                                    onClick = {
                                        presenterManager.setAnnouncementText(AnnouncementsViewModel.formatTimer(viewModel.timerRemaining))
                                        presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(Res.string.go_live), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (onAddToSchedule != null) {
                                Button(
                                    onClick = { onAddToSchedule.invoke(viewModel.buildSettings()) },
                                    enabled = viewModel.text.isNotBlank() || viewModel.timerHours > 0 || viewModel.timerMinutes > 0 || viewModel.timerSeconds > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(Res.string.add_to_schedule), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    }

                    val expiredTextField: @Composable (Modifier) -> Unit = { mod ->
                    // ── Expired text field ──────────────────────
                    Column(
                        modifier = mod,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SectionLabel(stringResource(Res.string.timer_expired_text_label))
                        OutlinedTextField(
                            value = viewModel.timerExpiredText,
                            onValueChange = { viewModel.setTimerExpiredText(it); viewModel.saveToSettings(onSettingsChange) },
                            placeholder = {
                                Text(
                                    text = stringResource(Res.string.timer_expired_text_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                    }

                    if (timerIsNarrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            timerControls(Modifier.fillMaxWidth())
                            expiredTextField(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            timerControls(Modifier.weight(1f))
                            expiredTextField(Modifier.weight(1f))
                        }
                    }
                }
            }

        } // end inner scrollable Column
    } // end outer Column
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
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
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

@Composable
private fun TimerStepButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.width(28.dp).height(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
