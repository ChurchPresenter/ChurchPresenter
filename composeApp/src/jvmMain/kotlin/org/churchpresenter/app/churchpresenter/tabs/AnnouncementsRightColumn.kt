package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.AnnouncementsViewModel
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.sharedui.composables.SlimSlider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import org.churchpresenter.theme.AppShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.announcement_animation
import org.churchpresenter.strings.generated.resources.announcement_animation_speed
import org.churchpresenter.strings.generated.resources.announcement_loop_count
import org.churchpresenter.strings.generated.resources.announcement_loop_tooltip
import org.churchpresenter.strings.generated.resources.preview
import org.churchpresenter.strings.generated.resources.canvas_text_bg_color
import org.churchpresenter.strings.generated.resources.transparent_default
import org.churchpresenter.strings.generated.resources.position_on_screen
import org.churchpresenter.sharedui.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.composables.rememberTextBackdropPainter
import org.churchpresenter.sharedui.utils.Utils
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.composables.ScreenPositionPicker
import androidx.compose.runtime.State
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import org.churchpresenter.sharedui.utils.PreviewOutput
import org.churchpresenter.sharedui.composables.bibleInsetFill
import org.churchpresenter.sharedui.composables.bibleListCard

/*
 * The Announcements tab's right column: the preview of what goes on screen, and where it sits,
 * what it sits on and how it moves.
 */

@Composable
internal fun AnnouncementsTabScope.AnnouncementsRightColumn(viewModel: AnnouncementsViewModel, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxHeight().padding(top = 4.dp).bibleListCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Which output this preview stands for. Announcements can be routed to several
        // differently-shaped outputs at once, so the operator says which; the picker
        // draws nothing until there is more than one to choose between.
        val previewOutput = rememberPreviewOutput(
            appSettings, Constants.PREVIEW_TAB_ANNOUNCEMENTS, Presenting.ANNOUNCEMENTS
        )
        PreviewOutputPicker(
            settings = appSettings,
            tabId = Constants.PREVIEW_TAB_ANNOUNCEMENTS,
            mode = Presenting.ANNOUNCEMENTS,
            onSettingsChange = onSettingsChange,
        )
        AnnouncementsPreview(viewModel, previewOutput, Modifier.weight(1f).fillMaxWidth())
        // Where the announcement sits, what it sits on and how it moves, stacked along the
        // bottom. Shared by the text and the timer, so it lives here rather than in either card.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnnouncementsPositionCard(viewModel)
            AnnouncementsBackgroundCard(viewModel)
            AnnouncementsAnimationCard(viewModel)
        }
    }
}

@Composable
private fun AnnouncementsTabScope.AnnouncementsPreview(
    viewModel: AnnouncementsViewModel,
    previewOutput: PreviewOutput,
    modifier: Modifier,
) {
    var previewWidthPx by remember { mutableStateOf(0) }
    var previewHeightPx by remember { mutableStateOf(0) }
    // Both the frame and the type inside it are measured against the SAME output.
    // They used to read two different things -- the box took the first non-primary
    // monitor's ratio while the scale divided by that monitor's width -- so on any
    // rig where announcements go somewhere else the preview was wrong twice over.
    val scaleFactor = if (previewWidthPx > 0)
        (previewWidthPx / density.density) / previewOutput.size.width.toFloat()
    else 0.1f
    val scaledFontSize = (viewModel.fontSize * scaleFactor).coerceAtLeast(4f).sp
    val scaledPadH = (32 * scaleFactor).coerceAtLeast(1f).dp
    val scaledPadV = (16 * scaleFactor).coerceAtLeast(1f).dp
    val previewFontFamily = remember(viewModel.fontType) {
        Utils.systemFontFamilyOrDefault(viewModel.fontType)
    }
    val previewTextStyle = TextStyle(
        fontFamily = previewFontFamily,
        fontWeight = if (viewModel.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (viewModel.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (viewModel.underline) TextDecoration.Underline else TextDecoration.None,
    )
    // The preview fits the output's shape into whatever height the controls below leave, so a
    // portrait output fills the height instead of running off the bottom.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(previewOutput.size.aspectRatio, matchHeightConstraintsFirst = true)
                .testTag(ANNOUNCEMENTS_PREVIEW_TAG)
                .clip(AppShape(4.dp))
                .background(Color.Black)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    AppShape(4.dp)
                )
                .onSizeChanged { size ->
                    previewWidthPx = size.width
                    previewHeightPx = size.height
                }
        ) {
            val previewContainerWidthPx = previewWidthPx.toFloat()
            val previewContainerHeightPx = previewHeightPx.toFloat()
            key(viewModel.scrollDurationMs, viewModel.movesPositive) {
                val infiniteTransition = rememberInfiniteTransition(label = "previewScroll")
                val offsetFractionState = infiniteTransition.animateFloat(
                        initialValue = if (viewModel.movesPositive) -1f else 1f,
                        targetValue  = if (viewModel.movesPositive) 1f else -1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = viewModel.scrollDurationMs, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "previewOffset"
                    )
                val look = AnnouncementPreviewLook(
                    text = viewModel.previewText,
                    textStyle = previewTextStyle,
                    fontSize = scaledFontSize,
                    pad = DpSize(scaledPadH, scaledPadV),
                    scaleFactor = scaleFactor,
                    textAlign = viewModel.previewTextAlign,
                )
                if (viewModel.isDirectional) {
                    DirectionalPreview(viewModel, 
                        look, viewModel.isHorizontal, viewModel.slideAlignment,
                        previewContainerWidthPx, previewContainerHeightPx, offsetFractionState,
                    )
                } else {
                    StaticPreview(viewModel, look, viewModel.isShowingLiveTimerValue, viewModel.durationMs)
                }
            }
        } // end preview Box
    } // end preview area
}

/** How the preview's text is drawn, at the preview's own scale. */
internal class AnnouncementPreviewLook(
    val text: String,
    val textStyle: TextStyle,
    val fontSize: TextUnit,
    val pad: DpSize,
    val scaleFactor: Float,
    val textAlign: TextAlign,
)

/** The text sliding across the preview, repeating, as the configured animation moves it on screen. */
@Composable
private fun AnnouncementsTabScope.DirectionalPreview(
    viewModel: AnnouncementsViewModel,
    look: AnnouncementPreviewLook,
    isHorizontal: Boolean,
    slideAlignment: Alignment,
    previewContainerWidthPx: Float,
    previewContainerHeightPx: Float,
    offsetFractionState: State<Float>,
) {
    val offsetFraction by offsetFractionState
    val previewText = look.text
    val previewTextStyle = look.textStyle
    val scaledFontSize = look.fontSize
    val scaledPadH = look.pad.width
    val scaledPadV = look.pad.height
    val scaleFactor = look.scaleFactor
    val previewTextAlign = look.textAlign
    val textComposable: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .then(if (isHorizontal) Modifier.wrapContentWidth(unbounded = true) else Modifier)
                .wrapContentHeight()
                .background(
                    previewTextBackground(viewModel.backgroundColor),
                    AppShape(2.dp)
                )
                .padding(horizontal = scaledPadH, vertical = scaledPadV),
            contentAlignment = Alignment.Center
        ) {
            // The preview draws the presenter's text at `scaleFactor` of
            // its output size, so the backdrop is drawn at that factor
            // too — otherwise an outline configured against a 1080p
            // screen is painted full size around thumbnail type.
            val previewBackdrop =
                rememberTextBackdropPainter(viewModel.backdrop, scaleFactor)
            Text(
                text = previewText.ifBlank { stringResource(Res.string.preview) },
                style = previewTextStyle,
                fontSize = scaledFontSize,
                color = Utils.parseHexColor(viewModel.textColor),
                textAlign = previewTextAlign,
                softWrap = !isHorizontal,
                modifier = previewBackdrop.modifier,
                onTextLayout = previewBackdrop::onTextLayout,
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        if (isHorizontal) {
            Box(
                modifier = Modifier
                    .align(slideAlignment)
                    .graphicsLayer { translationX = previewContainerWidthPx * offsetFraction },
                contentAlignment = Alignment.Center
            ) { textComposable() }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(slideAlignment)
                    .graphicsLayer { translationY = previewContainerHeightPx * offsetFraction },
                contentAlignment = Alignment.Center
            ) { textComposable() }
        }
    }
}

/** The text in its place on the preview, faded in and out where the animation fades. */
@Composable
private fun AnnouncementsTabScope.StaticPreview(
    viewModel: AnnouncementsViewModel,
    look: AnnouncementPreviewLook,
    isShowingLiveTimerValue: Boolean,
    durationMs: Int,
) {
    val previewText = look.text
    val previewTextStyle = look.textStyle
    val scaledFontSize = look.fontSize
    val scaledPadH = look.pad.width
    val scaledPadV = look.pad.height
    val scaleFactor = look.scaleFactor
    val previewTextAlign = look.textAlign
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
    val previewKey = Triple(previewText, viewModel.animationType, viewModel.position)
    AnimatedContent(
        targetState = previewKey,
        transitionSpec = {
            if (!isShowingLiveTimerValue && viewModel.animationType == Constants.ANIMATION_FADE)
                fadeIn(tween(previewDuration)) togetherWith
                    fadeOut(tween(previewDuration))
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
                        previewTextBackground(viewModel.backgroundColor),
                        AppShape(2.dp)
                    )
                    .padding(horizontal = scaledPadH, vertical = scaledPadV),
                contentAlignment = Alignment.Center
            ) {
                val previewBackdrop =
                    rememberTextBackdropPainter(viewModel.backdrop, scaleFactor)
                Text(
                    text = text.ifBlank { stringResource(Res.string.preview) },
                    style = previewTextStyle,
                    fontSize = scaledFontSize,
                    color = Utils.parseHexColor(viewModel.textColor),
                    textAlign = previewTextAlign,
                    modifier = previewBackdrop.modifier,
                    onTextLayout = previewBackdrop::onTextLayout,
                )
            }
        }
    }
}

@Composable
private fun AnnouncementsTabScope.AnnouncementsPositionCard(viewModel: AnnouncementsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bibleInsetFill(), AppShape(11.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Position on screen
        SectionLabel(stringResource(Res.string.position_on_screen))
        ScreenPositionPicker(
            positions = positions,
            selected = viewModel.position,
            onSelect = { posConst ->
                viewModel.setPosition(posConst)
                viewModel.saveToSettings(onSettingsChange)
            },
        )
    }
}

@Composable
private fun AnnouncementsTabScope.AnnouncementsBackgroundCard(viewModel: AnnouncementsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bibleInsetFill(), AppShape(11.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Background color
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (viewModel.backgroundColor == Constants.COLOR_VALUE_TRANSPARENT) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clickable {
                            viewModel.setBackgroundColor("#000000")
                            viewModel.saveToSettings(onSettingsChange)
                        }
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), AppShape(8.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(19.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, AppShape(2.dp))
                    )
                    Text(
                        text = stringResource(Res.string.transparent_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                val colorField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    ColorPickerField(
                        label = stringResource(Res.string.canvas_text_bg_color),
                        color = viewModel.backgroundColor,
                        onColorChange = {
                            viewModel.setBackgroundColor(it)
                            viewModel.saveToSettings(onSettingsChange)
                        },
                        modifier = fieldModifier,
                    )
                }
                val transparentLabel = stringResource(Res.string.transparent_default)
                val transparentButton: @Composable () -> Unit = {
                    KeyButton(
                        onClick = {
                            viewModel.setBackgroundColor("transparent")
                            viewModel.saveToSettings(onSettingsChange)
                        },
                        shape = AppShape(8.dp),
                        // Compact, so the color field beside it keeps its width.
                        contentPadding = PaddingValues(horizontal = ANNOUNCEMENT_TRANSPARENT_KEY_PADDING),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            transparentLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                // Side by side unless the button's label leaves the field too narrow to
                // read, as the longer translations' did -- then the button goes under it.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val measurer = rememberTextMeasurer()
                    val labelWidth = with(density) {
                        measurer.measure(transparentLabel, MaterialTheme.typography.labelMedium)
                            .size.width.toDp()
                    }
                    val fieldWidth = maxWidth - labelWidth - ANNOUNCEMENT_TRANSPARENT_KEY_PADDING * 2 - 8.dp
                    if (fieldWidth < ANNOUNCEMENT_MIN_BACKGROUND_FIELD_WIDTH) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            colorField(Modifier.fillMaxWidth())
                            transparentButton()
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            colorField(Modifier.weight(1f))
                            transparentButton()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnnouncementsTabScope.AnnouncementsAnimationCard(viewModel: AnnouncementsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bibleInsetFill(), AppShape(11.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Animation and loop count, with the speed slider under them
        val sliderMin = 500f
        val sliderMax = 30000f
        val sliderSum = sliderMin + sliderMax
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Wraps the loop count under the animation when the card is too narrow
            // for both, rather than squeezing it until its label is cut off.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
            DropdownSelector(
                label = stringResource(Res.string.announcement_animation),
                items = animItems,
                selected = viewModel.selectedAnim,
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
            TooltipArea(
                tooltip = {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            stringResource(Res.string.announcement_loop_tooltip),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(
                    anchor = Alignment.BottomCenter,
                    offset = DpOffset(0.dp, 4.dp),
                )
            ) {
                NumberSettingsTextField(
                    label = stringResource(Res.string.announcement_loop_count),
                    initialText = viewModel.loopCount,
                    range = 0..99,
                    onValueChange = { v ->
                        viewModel.setLoopCount(v)
                        viewModel.saveToSettings(onSettingsChange)
                    }
                )
            }
            } // end inner Row (animation + loop count)
            SectionLabel(stringResource(Res.string.announcement_animation_speed))
            SlimSlider(
                value = (sliderSum - viewModel.durationMs.toFloat()),
                onValueChange = { v ->
                    val dur = (sliderSum - v)
                    val snapped = (dur / sliderMin).toInt() * sliderMin.toInt()
                    viewModel.setAnimationDuration(
                        snapped.coerceIn(sliderMin.toInt(), sliderMax.toInt())
                    )
                    viewModel.saveToSettings(onSettingsChange)
                },
                valueRange = sliderMin..sliderMax,
                trailingLabel =
                    "${"%.1f".format((sliderSum - viewModel.durationMs) / ANNOUNCEMENT_MILLIS_PER_SECOND_F)}s",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
