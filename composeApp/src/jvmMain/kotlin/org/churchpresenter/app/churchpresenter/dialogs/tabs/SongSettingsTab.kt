package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom_left
import churchpresenter.composeapp.generated.resources.bottom_right
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.every_page
import churchpresenter.composeapp.generated.resources.first_page
import churchpresenter.composeapp.generated.resources.font_preview_text
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.lyrics
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.position_on_screen
import churchpresenter.composeapp.generated.resources.show_number
import churchpresenter.composeapp.generated.resources.show_title
import churchpresenter.composeapp.generated.resources.song_number
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.top_left
import churchpresenter.composeapp.generated.resources.top_right
import churchpresenter.composeapp.generated.resources.vertical_alignment
import churchpresenter.composeapp.generated.resources.word_wrap
import androidx.compose.foundation.layout.size
import churchpresenter.composeapp.generated.resources.auto_fit
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.text_margins
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.animation_slide_left
import churchpresenter.composeapp.generated.resources.animation_slide_right
import churchpresenter.composeapp.generated.resources.animation_type
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.song_transition_settings
import churchpresenter.composeapp.generated.resources.transition_duration
import androidx.compose.material3.Slider
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.PositionButtons
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource


@Composable
fun SongSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    presenterManager: PresenterManager? = null
) {
    val availableFonts = remember { Utils.getAvailableSystemFonts() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .widthIn(min = 400.dp, max = 450.dp)
                    .heightIn(min = 600.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
            ) {
                LeftColumn(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    availableFonts = availableFonts
                )
            }

            // Right Column
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .widthIn(min = 400.dp, max = 450.dp)
                    .heightIn(min = 600.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
            ) {
                RightColumn(settings, onSettingsChange, availableFonts, presenterManager)
            }
        }
    }
}

@Composable
private fun LeftColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>
) {

    // Store string resources to avoid calling stringResource in callbacks
    val noneStr = stringResource(Res.string.none)
    val firstPageStr = stringResource(Res.string.first_page)
    val everyPageStr = stringResource(Res.string.every_page)

    // Title Section
    SectionHeader(stringResource(Res.string.title))

    Spacer(modifier = Modifier.height(8.dp))

    SettingRow(stringResource(Res.string.show_title)) {
        DropdownSettingsField(
            value = when (settings.songSettings.titleDisplay) {
                Constants.NONE -> noneStr
                Constants.FIRST_PAGE -> firstPageStr
                Constants.EVERY_PAGE -> everyPageStr
                else -> firstPageStr
            },
            options = listOf(noneStr, firstPageStr, everyPageStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    noneStr -> Constants.NONE
                    firstPageStr -> Constants.FIRST_PAGE
                    everyPageStr -> Constants.EVERY_PAGE
                    else -> Constants.FIRST_PAGE
                }
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(titleDisplay = storedValue))
                }
            }
        )
    }

    SettingRow(stringResource(Res.string.font_size)) {
        NumberSettingsTextField(
            initialText = settings.songSettings.titleFontSize,
            onValueChange = {
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(titleFontSize = it))
                }
            },
            range = 8..150
        )
    }

    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontSettingsDropdown(
                modifier = Modifier.width(200.dp),
                value = settings.songSettings.titleFontType,
                fonts = availableFonts,
                onValueChange = {
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(titleFontType = it))
                    }
                }
            )
            val previewFontFamily = remember(settings.songSettings.titleFontType) {
                systemFontFamilyOrDefault(settings.songSettings.titleFontType)
            }
            Text(
                text = stringResource(Res.string.font_preview_text),
                fontFamily = previewFontFamily,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp)
            )
        }
    }

    SettingRow(stringResource(Res.string.color)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                color = settings.songSettings.titleColor,
                onColorChange = {
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(titleColor = it))
                    }
                }
            )
            TextStyleButtons(
                bold = settings.songSettings.titleBold,
                italic = settings.songSettings.titleItalic,
                underline = settings.songSettings.titleUnderline,
                shadow = settings.songSettings.titleShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleShadow = it)) } }
            )
        }
    }

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        PositionButtons(
            selectedPosition = settings.songSettings.titlePosition,
            onPositionChange = { storedValue ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(titlePosition = storedValue))
                }
            },
            aboveValue = Constants.ABOVE_VERSE,
            belowValue = Constants.BELOW_VERSE
        )
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        HorizontalAlignmentButtons(
            selectedAlignment = settings.songSettings.titleHorizontalAlignment,
            onAlignmentChange = { storedValue ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(titleHorizontalAlignment = storedValue))
                }
            },
            leftValue = Constants.LEFT,
            centerValue = Constants.CENTER,
            rightValue = Constants.RIGHT
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Transition Section
    SectionHeader(stringResource(Res.string.song_transition_settings))

    Spacer(modifier = Modifier.height(8.dp))

    // Transition duration slider
    val durationLabel = stringResource(Res.string.transition_duration)
    val msSuffix = stringResource(Res.string.milliseconds_suffix)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = durationLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(120.dp)
        )
        Slider(
            value = settings.songSettings.transitionDuration,
            onValueChange = { rawValue ->
                val snapped = (rawValue / 50f).toInt() * 50f
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(transitionDuration = snapped)) }
            },
            valueRange = 100f..2000f,
            steps = 37,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${settings.songSettings.transitionDuration.toInt()}$msSuffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(60.dp)
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Animation type dropdown
    SettingRow(stringResource(Res.string.animation_type)) {
        val crossfadeText = stringResource(Res.string.animation_crossfade)
        val fadeText = stringResource(Res.string.animation_fade)
        val slideLeftText = stringResource(Res.string.animation_slide_left)
        val slideRightText = stringResource(Res.string.animation_slide_right)
        val noneText = stringResource(Res.string.animation_none)

        val currentType = when (settings.songSettings.animationType) {
            Constants.ANIMATION_FADE -> AnimationType.FADE
            Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
            Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
            Constants.ANIMATION_NONE -> AnimationType.NONE
            else -> AnimationType.CROSSFADE
        }

        DropdownSelector(
            modifier = Modifier.width(200.dp),
            label = "",
            items = listOf(crossfadeText, fadeText, slideLeftText, slideRightText, noneText),
            selected = when (currentType) {
                AnimationType.CROSSFADE -> crossfadeText
                AnimationType.FADE -> fadeText
                AnimationType.SLIDE_LEFT -> slideLeftText
                AnimationType.SLIDE_RIGHT -> slideRightText
                AnimationType.NONE -> noneText
            },
            onSelectedChange = { selected ->
                val newType = when (selected) {
                    fadeText -> Constants.ANIMATION_FADE
                    slideLeftText -> Constants.ANIMATION_SLIDE_LEFT
                    slideRightText -> Constants.ANIMATION_SLIDE_RIGHT
                    noneText -> Constants.ANIMATION_NONE
                    else -> Constants.ANIMATION_CROSSFADE
                }
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(animationType = newType)) }
            }
        )
    }

    // ── Text Margins ──
    SectionHeader(stringResource(Res.string.text_margins))
    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(Res.string.top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                NumberSettingsTextField(
                    modifier = Modifier.width(100.dp),
                    initialText = settings.songSettings.marginTop,
                    onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginTop = value)) } },
                    range = 0..500
                )
            }
            // Left | Screen | Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(Res.string.left), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        modifier = Modifier.width(100.dp),
                        initialText = settings.songSettings.marginLeft,
                        onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginLeft = value)) } },
                        range = 0..500
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(Res.string.screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(Res.string.right), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        modifier = Modifier.width(100.dp),
                        initialText = settings.songSettings.marginRight,
                        onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginRight = value)) } },
                        range = 0..500
                    )
                }
            }
            // Bottom
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(Res.string.bottom), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                NumberSettingsTextField(
                    modifier = Modifier.width(100.dp),
                    initialText = settings.songSettings.marginBottom,
                    onValueChange = { value -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(marginBottom = value)) } },
                    range = 0..500
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun RightColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>,
    presenterManager: PresenterManager? = null
) {
    // Store string resources to avoid calling stringResource in callbacks
    val topLeftStr = stringResource(Res.string.top_left)
    val topRightStr = stringResource(Res.string.top_right)
    val bottomLeftStr = stringResource(Res.string.bottom_left)
    val bottomRightStr = stringResource(Res.string.bottom_right)
    val noneStr = stringResource(Res.string.none)
    val firstPageStr = stringResource(Res.string.first_page)
    val everyPageStr = stringResource(Res.string.every_page)

    // Lyrics Section
    SectionHeader(stringResource(Res.string.lyrics))

    Spacer(modifier = Modifier.height(8.dp))

    val textMeasurer = rememberTextMeasurer()
    val isPresentingLyrics = if (presenterManager != null) {
        remember { derivedStateOf {
            presenterManager.presentingMode.value == Presenting.LYRICS &&
            presenterManager.lyricSection.value.lines.any { line ->
                !line.startsWith(Constants.VERSE_RUS, ignoreCase = true) &&
                !line.startsWith(Constants.CHORUS_RUS, ignoreCase = true) &&
                !line.startsWith(Constants.VERSE, ignoreCase = true) &&
                !line.startsWith(Constants.CHORUS, ignoreCase = true) &&
                line.isNotBlank()
            }
        } }.value
    } else false
    val activeScreens = listOf(
        settings.projectionSettings.screen1Assignment,
        settings.projectionSettings.screen2Assignment,
        settings.projectionSettings.screen3Assignment,
        settings.projectionSettings.screen4Assignment
    ).take(settings.projectionSettings.numberOfWindows)
    val hasFullscreenScreen = activeScreens.any { it.displayMode == Constants.DISPLAY_MODE_FULLSCREEN }
    val hasLowerThirdScreen = activeScreens.any { it.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD }

    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NumberSettingsTextField(
                        initialText = settings.songSettings.lyricsFontSize,
                        onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsFontSize = it)) } },
                        range = 8..150
                    )
                    if (presenterManager != null) {
                        TextButton(
                            enabled = isPresentingLyrics && hasFullscreenScreen,
                            onClick = {
                                val section = presenterManager.lyricSection.value
                                val lyricsText = section.lines.filter { line ->
                                    !line.startsWith(Constants.VERSE_RUS, ignoreCase = true) &&
                                    !line.startsWith(Constants.CHORUS_RUS, ignoreCase = true) &&
                                    !line.startsWith(Constants.VERSE, ignoreCase = true) &&
                                    !line.startsWith(Constants.CHORUS, ignoreCase = true)
                                }.joinToString("\n")
                                if (lyricsText.isBlank()) return@TextButton
                                val ss = settings.songSettings
                                val proj = settings.projectionSettings
                                val baseStyle = TextStyle(
                                    fontFamily = systemFontFamilyOrDefault(ss.lyricsFontType),
                                    fontWeight = if (ss.lyricsBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (ss.lyricsItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (ss.lyricsUnderline) TextDecoration.Underline else TextDecoration.None
                                )
                                val availW = 1920 - proj.windowLeft - proj.windowRight - ss.marginLeft - ss.marginRight
                                val availH = 1080 - proj.windowTop - proj.windowBottom - ss.marginTop - ss.marginBottom
                                val shouldShowTitle = ss.titleDisplay != Constants.NONE && section.title.isNotBlank()
                                val titleH = if (shouldShowTitle) {
                                    val titleStyle = TextStyle(
                                        fontFamily = systemFontFamilyOrDefault(ss.titleFontType),
                                        fontWeight = if (ss.titleBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (ss.titleItalic) FontStyle.Italic else FontStyle.Normal
                                    )
                                    val titleResult = textMeasurer.measure(section.title, titleStyle.copy(fontSize = ss.titleFontSize.sp), density = Density(1f))
                                    titleResult.size.height
                                } else 0
                                val fullSize = calculateAutoFitFontSize(textMeasurer, lyricsText, baseStyle, availW, availH - titleH)
                                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsFontSize = fullSize)) }
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NumberSettingsTextField(
                        initialText = settings.songSettings.lyricsLowerThirdFontSize,
                        onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdFontSize = it)) } },
                        range = 8..150
                    )
                    if (presenterManager != null) {
                        TextButton(
                            enabled = isPresentingLyrics && hasLowerThirdScreen,
                            onClick = {
                                val section = presenterManager.lyricSection.value
                                val lyricsText = section.lines.filter { line ->
                                    !line.startsWith(Constants.VERSE_RUS, ignoreCase = true) &&
                                    !line.startsWith(Constants.CHORUS_RUS, ignoreCase = true) &&
                                    !line.startsWith(Constants.VERSE, ignoreCase = true) &&
                                    !line.startsWith(Constants.CHORUS, ignoreCase = true)
                                }.joinToString("\n")
                                if (lyricsText.isBlank()) return@TextButton
                                val ss = settings.songSettings
                                val proj = settings.projectionSettings
                                val baseStyle = TextStyle(
                                    fontFamily = systemFontFamilyOrDefault(ss.lyricsFontType),
                                    fontWeight = if (ss.lyricsBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (ss.lyricsItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (ss.lyricsUnderline) TextDecoration.Underline else TextDecoration.None
                                )
                                val availW = 1920 - proj.windowLeft - proj.windowRight - ss.marginLeft - ss.marginRight
                                val availH = 1080 - proj.windowTop - proj.windowBottom - ss.marginTop - ss.marginBottom
                                val ltH = (availH * proj.lowerThirdHeightPercent / 100f).toInt()
                                val shouldShowTitle = ss.titleDisplay != Constants.NONE && section.title.isNotBlank()
                                val titleH = if (shouldShowTitle) {
                                    val titleStyle = TextStyle(
                                        fontFamily = systemFontFamilyOrDefault(ss.titleFontType),
                                        fontWeight = if (ss.titleBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (ss.titleItalic) FontStyle.Italic else FontStyle.Normal
                                    )
                                    val titleResult = textMeasurer.measure(section.title, titleStyle.copy(fontSize = ss.titleFontSize.sp), density = Density(1f))
                                    titleResult.size.height
                                } else 0
                                val ltSize = calculateAutoFitFontSize(textMeasurer, lyricsText, baseStyle, availW, ltH - titleH)
                                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdFontSize = ltSize)) }
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FontSettingsDropdown(
                modifier = Modifier.width(200.dp),
                value = settings.songSettings.lyricsFontType,
                fonts = availableFonts,
                onValueChange = {
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(lyricsFontType = it))
                    }
                }
            )
            val previewFontFamily = remember(settings.songSettings.lyricsFontType) {
                systemFontFamilyOrDefault(settings.songSettings.lyricsFontType)
            }
            Text(
                text = stringResource(Res.string.font_preview_text),
                fontFamily = previewFontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp)
            )
        }
    }

    SettingRow(stringResource(Res.string.color)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                color = settings.songSettings.lyricsColor,
                onColorChange = {
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(lyricsColor = it))
                    }
                }
            )
            TextStyleButtons(
                bold = settings.songSettings.lyricsBold,
                italic = settings.songSettings.lyricsItalic,
                underline = settings.songSettings.lyricsUnderline,
                shadow = settings.songSettings.lyricsShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsShadow = it)) } }
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var initialWordWrapValue by remember { mutableStateOf(settings.songSettings.wordWrap) }
        Checkbox(
            checked = initialWordWrapValue,
            onCheckedChange = {
                initialWordWrapValue = it
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(wordWrap = it))
                }
            }
        )
        Text(
            text = stringResource(Res.string.word_wrap),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(5.dp))

    SettingRow(stringResource(Res.string.vertical_alignment), width = 200.dp) {
        VerticalAlignmentButtons(
            selectedAlignment = settings.songSettings.lyricsAlignment,
            onAlignmentChange = { storedValue ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(lyricsAlignment = storedValue))
                }
            },
            topValue = Constants.TOP,
            middleValue = Constants.MIDDLE,
            bottomValue = Constants.BOTTOM
        )
    }

    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.songSettings.lyricsHorizontalAlignment,
                    onAlignmentChange = { storedValue ->
                        onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsHorizontalAlignment = storedValue)) }
                    },
                    leftValue = Constants.LEFT,
                    centerValue = Constants.CENTER,
                    rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = settings.songSettings.lyricsLowerThirdHorizontalAlignment,
                    onAlignmentChange = { storedValue ->
                        onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsLowerThirdHorizontalAlignment = storedValue)) }
                    },
                    leftValue = Constants.LEFT,
                    centerValue = Constants.CENTER,
                    rightValue = Constants.RIGHT
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Song Number Section
    SectionHeader(stringResource(Res.string.song_number))

    Spacer(modifier = Modifier.height(8.dp))

    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.songSettings.songNumberFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberFontSize = it)) } },
                    range = 8..150
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberSettingsTextField(
                    initialText = settings.songSettings.songNumberLowerThirdFontSize,
                    onValueChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberLowerThirdFontSize = it)) } },
                    range = 8..150
                )
            }
        }
    }

    SettingRow(stringResource(Res.string.show_number)) {
        DropdownSettingsField(
            value = when (settings.songSettings.titleDisplay) {
                Constants.NONE -> noneStr
                Constants.FIRST_PAGE -> firstPageStr
                Constants.EVERY_PAGE -> everyPageStr
                else -> firstPageStr
            },
            options = listOf(noneStr, firstPageStr, everyPageStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    noneStr -> Constants.NONE
                    firstPageStr -> Constants.FIRST_PAGE
                    everyPageStr -> Constants.EVERY_PAGE
                    else -> Constants.FIRST_PAGE
                }
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(showNumber = storedValue))
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(5.dp))

    val pos = when (settings.songSettings.songNumberPosition) {
        Constants.TOP_LEFT -> topLeftStr
        Constants.TOP_RIGHT -> topRightStr
        Constants.BOTTOM_LEFT -> bottomLeftStr
        Constants.BOTTOM_RIGHT -> bottomRightStr
        else -> bottomRightStr
    }

    var initialPosition by remember { mutableStateOf(pos) }
    SettingRow(stringResource(Res.string.position_on_screen), width = 200.dp) {
        DropdownSettingsField(
            value = initialPosition,
            options = listOf(topLeftStr, topRightStr, bottomLeftStr, bottomRightStr),
            onValueChange = { displayValue ->
                val storedValue = when (displayValue) {
                    topLeftStr -> Constants.TOP_LEFT
                    topRightStr -> Constants.TOP_RIGHT
                    bottomLeftStr -> Constants.BOTTOM_LEFT
                    bottomRightStr -> Constants.BOTTOM_RIGHT
                    else -> Constants.BOTTOM_RIGHT
                }
                initialPosition = storedValue
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(songNumberPosition = storedValue))
                }
            }
        )
    }

    SettingRow(stringResource(Res.string.color)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPickerField(
                color = settings.songSettings.songNumberColor,
                onColorChange = {
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(songNumberColor = it))
                    }
                }
            )
            TextStyleButtons(
                bold = settings.songSettings.songNumberBold,
                italic = settings.songSettings.songNumberItalic,
                underline = settings.songSettings.songNumberUnderline,
                shadow = settings.songSettings.songNumberShadow,
                onBoldChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberBold = it)) } },
                onItalicChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberItalic = it)) } },
                onUnderlineChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberUnderline = it)) } },
                onShadowChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberShadow = it)) } }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    width: Dp = 120.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(width)
        )
        content()
    }
}


