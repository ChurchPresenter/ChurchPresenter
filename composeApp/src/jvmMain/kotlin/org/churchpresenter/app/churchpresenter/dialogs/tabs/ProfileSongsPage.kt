package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.lyrics
import churchpresenter.composeapp.generated.resources.middle
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.profile_end_marker
import churchpresenter.composeapp.generated.resources.profile_end_marker_spacing
import churchpresenter.composeapp.generated.resources.profile_group_languages
import churchpresenter.composeapp.generated.resources.profile_group_slides
import churchpresenter.composeapp.generated.resources.profile_group_text
import churchpresenter.composeapp.generated.resources.profile_layout
import churchpresenter.composeapp.generated.resources.profile_ref_above
import churchpresenter.composeapp.generated.resources.profile_ref_after
import churchpresenter.composeapp.generated.resources.profile_repeat_chorus
import churchpresenter.composeapp.generated.resources.profile_section_label
import churchpresenter.composeapp.generated.resources.profile_section_label_sub
import churchpresenter.composeapp.generated.resources.profile_slide_element
import churchpresenter.composeapp.generated.resources.profile_song_position
import churchpresenter.composeapp.generated.resources.profile_text_style
import churchpresenter.composeapp.generated.resources.profile_title_slide
import churchpresenter.composeapp.generated.resources.profile_title_slide_sub
import churchpresenter.composeapp.generated.resources.profile_title_slide_valign
import churchpresenter.composeapp.generated.resources.profile_word_wrap
import churchpresenter.composeapp.generated.resources.top
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The Songs page, the Bible page's twin: background, text, languages, the slides themselves, where
 * the lyrics sit, the band and the fades.
 *
 * The Text group's strip picks which language of a song the rows edit -- only for the two elements
 * a song carries per language -- and which element: the lyrics, the title slide, the title, the
 * number, the look-ahead and the next-section line.
 */
@Composable
internal fun ProfileSongsPage(
    draft: AppSettings,
    profile: OutputProfile,
    element: CustomizeElement,
    onElementChange: (CustomizeElement) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
    onOpenPage: (ProfilePage) -> Unit,
) {
    val lowerThird = profile.isLowerThird
    val song = draft.songSettings
    // A value, not a local `fun`: a reference to a local function handed to a child is remembered
    // across compositions, and would keep writing through the document as it was when first drawn.
    val updateSong: ((SongSettings) -> SongSettings) -> Unit = { transform ->
        onSettingsChange { s -> s.copy(songSettings = transform(s.songSettings)) }
    }

    ContentBackgroundGroup(
        scope = if (lowerThird) BackgroundScope.SONG_LOWER_THIRD else BackgroundScope.SONG,
        contentLabel = stringResource(Res.string.customize_songs),
        draft = draft,
        profile = profile,
        onProfileChange = onProfileChange,
        onSettingsChange = onSettingsChange,
        onOpenBackground = { onOpenPage(ProfilePage.Appearance(CustomizePane.BACKGROUND)) },
    )
    SongTextGroup(draft, profile, element, onElementChange, updateSong, onSettingsChange, onProfileChange)
    if (profile.songMode == Constants.SONG_LANG_BOTH) {
        SettingsGroup(stringResource(Res.string.profile_group_languages), paths = SONG_LAYOUT_PATHS) {
            SettingsRow(stringResource(Res.string.profile_layout), paths = SONG_LAYOUT_PATHS) {
                RowSegmented(
                    options = bilingualLayoutRowOptions(),
                    selected = song.bilingualLayout,
                    onSelect = { v -> updateSong { it.copy(bilingualLayout = v) } },
                )
            }
        }
    }
    SlidesGroup(song, lowerThird, updateSong)
    SongPlacementGroups(
        draft = draft,
        profile = profile,
        lyrics = element == CustomizeElement.SONG_LYRICS,
        updateSong = updateSong,
        onProfileChange = onProfileChange,
        onSettingsChange = onSettingsChange,
    )
}

/**
 * The Text group: the strip picking the language and element, then the element's look, then what
 * the element shows -- the song tab's own options for it.
 */
@Composable
private fun SongTextGroup(
    draft: AppSettings,
    profile: OutputProfile,
    element: CustomizeElement,
    onElementChange: (CustomizeElement) -> Unit,
    updateSong: ((SongSettings) -> SongSettings) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
) {
    val lowerThird = profile.isLowerThird
    val target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
    val song = draft.songSettings
    val titleSlideView = element == CustomizeElement.SONG_TITLE_SLIDE
    var language by remember(profile.id) { mutableStateOf(SongStyleLanguage.PRIMARY) }
    var slideElement by remember(profile.id) { mutableStateOf(SongStyleElement.TITLE) }
    val styleElement = if (titleSlideView) slideElement else element.toSongStyleElement()
    val styleLanguages = styleLanguagesFor(profile.songMode, profile.songTranslations)
    val perLanguage = styleLanguages.size > 1 && styleElement in SECOND_LANGUAGE_ELEMENTS
    val editingLanguage = if (perLanguage && language in styleLanguages) language else styleLanguages.first()

    val style = song.elementStyle(styleElement, target, editingLanguage)
    val elements = styleElementsFor(CustomizePane.SONGS, profile)
    val lookPaths = songLookPaths(song, styleElement, target, editingLanguage)
    SettingsGroup(
        caption = stringResource(Res.string.profile_group_text),
        paths = lookPaths.all,
        action = ResetAction(style != defaultSongElementStyle(styleElement, target)) {
            updateSong { it.withElementReset(styleElement, target, editingLanguage) }
        },
        header = {
            AppliesToStrip(
                targets = if (perLanguage) {
                    styleLanguages.map { RowOption(it, it.nameLabel(song), songLanguageTag(it)) }
                } else {
                    emptyList()
                },
                target = editingLanguage,
                onTarget = { language = it },
                elements = elements.map { RowOption(it, it.label(), elementChipTag(it.name)) },
                element = element,
                onElement = onElementChange,
            )
        },
    ) {
        if (titleSlideView) SlideElementRow(slideElement) { slideElement = it }
        key(styleElement, editingLanguage) {
            TextLookRows(
                look = style.toLook(styleElement),
                onChange = { look ->
                    updateSong { it.withElementStyle(styleElement, target, editingLanguage, style.withLook(look)) }
                },
                fonts = rememberSystemFonts(),
                paths = lookPaths,
                autoFitScope = if (!titleSlideView && styleElement.hasAutoFit) {
                    {
                        AutoFitScopeControl(
                            eachSlide = song.autoFitEachSlide(lowerThird),
                            onEachSlideChange = { v -> updateSong { it.withAutoFitEachSlide(lowerThird, v) } },
                        )
                    }
                } else {
                    null
                },
                extraBasic = {
                    // A cornered number is drawn over the slide and never in the row this places.
                    val cornered = styleElement == SongStyleElement.NUMBER &&
                        song.numberCorner(lowerThird) != Constants.NONE
                    if (styleElement.hasPosition && !titleSlideView && !cornered) {
                        SettingsRow(stringResource(Res.string.profile_song_position)) {
                            RowSegmented(
                                options = listOf(
                                    RowOption(Constants.ABOVE_VERSE, stringResource(Res.string.profile_ref_above)),
                                    RowOption(Constants.BELOW_VERSE, stringResource(Res.string.profile_ref_after)),
                                ),
                                selected = style.position,
                                onSelect = { v ->
                                    updateSong { it.withElementStyle(
                                        styleElement,
                                        target,
                                        editingLanguage,
                                        style.copy(position = v),
                                    ) }
                                },
                            )
                        }
                    }
                },
            )
        }
        // What the element shows and where the number goes: the song tab's own options for it,
        // which have no simpler row of their own -- the slide chunk, the languages on screen, when
        // the number and title appear, the number's corner, the title slide's own placements.
        SettingsWideRow {
            SongElementOptions(
                settings = draft,
                onSettingsChange = onSettingsChange,
                element = styleElement,
                target = target,
                titleSlideView = titleSlideView,
                outputMode = profile.songMode,
                onOutputModeChange = { onProfileChange(profile.copy(songMode = it)) },
            )
        }
    }
}

/** Which element of the title slide the Text rows edit. */
@Composable
private fun SlideElementRow(selected: SongStyleElement, onSelect: (SongStyleElement) -> Unit) {
    SettingsRow(stringResource(Res.string.profile_slide_element)) {
        RowSegmented(
            options = TITLE_SLIDE_ELEMENTS.map { RowOption(it, it.label()) },
            selected = selected,
            onSelect = onSelect,
        )
    }
}

/** Where the lyrics sit, the band, and the fades -- the groups the Bible page shares. */
@Composable
private fun SongPlacementGroups(
    draft: AppSettings,
    profile: OutputProfile,
    lyrics: Boolean,
    updateSong: ((SongSettings) -> SongSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val lowerThird = profile.isLowerThird
    val song = draft.songSettings
    val d = SongSettings()
    PositionGroup(
        paths = PositionPaths(
            vertical = listOf("songSettings.lyricsAlignment"),
            margins = listOf("marginTop", "marginBottom", "marginLeft", "marginRight").map { "songSettings.$it" },
            region = listOf("songSettings.layoutExtras.contentRegion"),
        ),
        verticalAlignment = song.lyricsAlignment,
        onVerticalAlignment = { v -> updateSong { it.copy(lyricsAlignment = v) } },
        margins = Margins(song.marginTop, song.marginBottom, song.marginLeft, song.marginRight),
        onMargins = { m ->
            updateSong { it.copy(
                marginTop = m.top,
                marginBottom = m.bottom,
                marginLeft = m.left,
                marginRight = m.right,
            ) }
        },
        region = song.layoutExtras.contentRegion.takeIf { !lowerThird },
        onRegion = { r -> updateSong { it.copy(layoutExtras = it.layoutExtras.copy(contentRegion = r)) } },
        reset = ResetAction(
            song.lyricsAlignment != d.lyricsAlignment || song.marginTop != d.marginTop ||
                song.marginBottom != d.marginBottom || song.marginLeft != d.marginLeft ||
                song.marginRight != d.marginRight || song.layoutExtras.contentRegion != d.layoutExtras.contentRegion,
        ) {
            updateSong {
                it.copy(
                    lyricsAlignment = d.lyricsAlignment,
                    marginTop = d.marginTop,
                    marginBottom = d.marginBottom,
                    marginLeft = d.marginLeft,
                    marginRight = d.marginRight,
                    layoutExtras = it.layoutExtras.copy(contentRegion = d.layoutExtras.contentRegion),
                )
            }
        },
        extraAdvanced = {
            // Vertical only: the lyric blocks fill the width, so there is no room to move sideways.
            if (!lowerThird && lyrics) {
                ElementPlacementRows(
                    offset = song.layoutExtras.lyricsOffset,
                    onChange = { v -> updateSong { it.copy(layoutExtras = it.layoutExtras.copy(lyricsOffset = v)) } },
                    verticalOnly = true,
                    tagPrefix = LYRICS_OFFSET_TAG,
                    paths = listOf("songSettings.layoutExtras.lyricsOffset"),
                )
            }
        },
    )

    if (lowerThird) {
        BandGroup(
            prefix = "songSettings",
            scope = BackgroundScope.SONG_LOWER_THIRD,
            heightPercent = song.lowerThirdHeightPercent,
            onHeight = { v -> updateSong { it.copy(lowerThirdHeightPercent = v) } },
            draft = draft,
            profile = profile,
            onProfileChange = onProfileChange,
            onSettingsChange = onSettingsChange,
            reset = ResetAction(song.lowerThirdHeightPercent != d.lowerThirdHeightPercent) {
                updateSong { it.copy(lowerThirdHeightPercent = d.lowerThirdHeightPercent) }
            },
        )
    }

    TransitionGroup(
        prefix = "songSettings",
        fadeIn = song.fadeIn,
        fadeOut = song.fadeOut,
        crossfade = song.crossfade,
        durationMs = song.transitionDuration,
        onFadeIn = { v -> updateSong { it.copy(fadeIn = v) } },
        onFadeOut = { v -> updateSong { it.copy(fadeOut = v) } },
        onCrossfade = { v -> updateSong { it.copy(crossfade = v) } },
        onDuration = { v -> updateSong { it.copy(transitionDuration = v) } },
        reset = ResetAction(
            song.fadeIn != d.fadeIn || song.fadeOut != d.fadeOut || song.crossfade != d.crossfade ||
                song.transitionDuration != d.transitionDuration,
        ) {
            updateSong {
                it.copy(
                    fadeIn = d.fadeIn,
                    fadeOut = d.fadeOut,
                    crossfade = d.crossfade,
                    transitionDuration = d.transitionDuration,
                )
            }
        },
    )
}

private val END_MARKER_SPACING = 0..20

/**
 * SLIDES: how a lyric slide is built and how a song opens and ends -- the title slide, word wrap,
 * the chorus repeated after every verse, the end-of-song marker and the section label above the
 * lyrics.
 */
@Composable
private fun SlidesGroup(song: SongSettings, lowerThird: Boolean, updateSong: ((SongSettings) -> SongSettings) -> Unit) {
    SettingsGroup(stringResource(Res.string.profile_group_slides), paths = SLIDES_PATHS) {
        SettingsSwitchRow(
            stringResource(Res.string.profile_title_slide),
            song.titleSlideEnabled,
            { v -> updateSong { it.copy(titleSlideEnabled = v) } },
            sub = stringResource(Res.string.profile_title_slide_sub),
            modifier = Modifier.testTag("song_titleSlideEnabled"),
            paths = listOf("songSettings.titleSlideEnabled"),
        )
        // A band keeps the title slide at its own bottom whatever this says.
        if (!lowerThird && song.titleSlideEnabled) {
            SettingsRow(
                stringResource(Res.string.profile_title_slide_valign),
                advanced = true,
                paths = listOf("songSettings.titleSlideVerticalAlignment"),
            ) {
                RowSegmented(
                    options = listOf(
                        RowOption(Constants.TOP, stringResource(Res.string.top)),
                        RowOption(Constants.MIDDLE, stringResource(Res.string.middle)),
                        RowOption(Constants.BOTTOM, stringResource(Res.string.bottom)),
                    ),
                    selected = song.titleSlideVerticalAlignment,
                    onSelect = { v -> updateSong { it.copy(titleSlideVerticalAlignment = v) } },
                )
            }
        }
        SettingsSwitchRow(
            stringResource(Res.string.profile_word_wrap),
            song.wordWrap,
            { v -> updateSong { it.copy(wordWrap = v) } },
            paths = listOf("songSettings.wordWrap"),
        )
        SettingsSwitchRow(
            stringResource(Res.string.profile_repeat_chorus),
            song.autoRepeatChorus,
            { v -> updateSong { it.copy(autoRepeatChorus = v) } },
            paths = listOf("songSettings.autoRepeatChorus"),
        )
        SettingsSwitchRow(
            stringResource(Res.string.profile_end_marker),
            song.showEndOfSongIndicator,
            { v -> updateSong { it.copy(showEndOfSongIndicator = v) } },
            advanced = true,
            paths = listOf("songSettings.showEndOfSongIndicator", "songSettings.endOfSongIndicatorSpacing"),
            extra = {
                if (song.showEndOfSongIndicator) {
                    RowStepper(
                        song.endOfSongIndicatorSpacing,
                        { v -> updateSong { it.copy(endOfSongIndicatorSpacing = v) } },
                        END_MARKER_SPACING,
                        unit = stringResource(Res.string.profile_end_marker_spacing),
                    )
                }
            },
        )
        SectionLabelRows(song.layoutExtras.sectionLabel, lowerThird) { transform ->
            updateSong { s ->
                s.copy(layoutExtras = s.layoutExtras.copy(sectionLabel = transform(s.layoutExtras.sectionLabel)))
            }
        }
    }
}

/**
 * The current section's own label ("Verse 1", "Chorus") above the lyrics: on or off, and -- Advanced
 * -- its size, colour, face and placement.
 */
@Composable
private fun SectionLabelRows(
    label: SongSectionLabel,
    lowerThird: Boolean,
    update: ((SongSectionLabel) -> SongSectionLabel) -> Unit,
) {
    SettingsSwitchRow(
        stringResource(Res.string.profile_section_label),
        label.enabled,
        { v -> update { it.copy(enabled = v) } },
        sub = stringResource(Res.string.profile_section_label_sub),
        paths = listOf("$SECTION_LABEL_PATH.enabled"),
    )
    if (!label.enabled) return
    SettingsRow(
        stringResource(Res.string.profile_section_label) + " · " + stringResource(Res.string.lyrics),
        advanced = true,
        paths = listOf("$SECTION_LABEL_PATH.fontSize", "$SECTION_LABEL_PATH.color"),
    ) {
        RowStepper(
            label.fontSize,
            { v -> update { it.copy(fontSize = v) } },
            SongSectionLabel.FONT_SIZE_RANGE,
            step = 2,
            unit = stringResource(Res.string.pixels_short),
        )
        RowColor(label.color, { v -> update { it.copy(color = v) } })
    }
    SettingsRow(
        stringResource(Res.string.profile_section_label) + " · " + stringResource(Res.string.profile_text_style),
        advanced = true,
        paths = listOf("bold", "italic", "underline", "shadow").map { "$SECTION_LABEL_PATH.$it" },
    ) {
        TextStyleButtons(
            bold = label.bold,
            italic = label.italic,
            underline = label.underline,
            shadow = label.shadow,
            onBoldChange = { v -> update { it.copy(bold = v) } },
            onItalicChange = { v -> update { it.copy(italic = v) } },
            onUnderlineChange = { v -> update { it.copy(underline = v) } },
            onShadowChange = { v -> update { it.copy(shadow = v) } },
            buttonSize = 26.dp,
        )
    }
    if (!lowerThird) {
        ElementPlacementRows(
            offset = label.offset,
            onChange = { v -> update { it.copy(offset = v) } },
            tagPrefix = SECTION_LABEL_OFFSET_TAG,
            paths = listOf("$SECTION_LABEL_PATH.offset"),
        )
    }
}

/** Which stored profile a Songs element stands for. */
private fun CustomizeElement.toSongStyleElement(): SongStyleElement = when (this) {
    CustomizeElement.SONG_TITLE -> SongStyleElement.TITLE
    CustomizeElement.SONG_NUMBER -> SongStyleElement.NUMBER
    CustomizeElement.SONG_LOOK_AHEAD -> SongStyleElement.LOOK_AHEAD
    CustomizeElement.SONG_NEXT_SECTION -> SongStyleElement.NEXT_SECTION
    else -> SongStyleElement.LYRICS
}

/** Test handle for one language of the Songs strip. */
internal fun songLanguageTag(language: SongStyleLanguage): String = "profile_song_language_${language.name}"

/** Test handle for the lyrics block's own positioning switch. */
internal const val LYRICS_OFFSET_TAG = "song_lyrics_offset"

/** Test handle for the section label's positioning switch. */
internal const val SECTION_LABEL_OFFSET_TAG = "song_section_label_offset"

/** Where the languages' layout is stored. */
private val SONG_LAYOUT_PATHS = listOf("songSettings.bilingualLayout")

/** Where the section label above the lyrics is stored. */
private const val SECTION_LABEL_PATH = "songSettings.layoutExtras.sectionLabel"

/** Everything the Slides group writes. */
private val SLIDES_PATHS = listOf(
    "songSettings.titleSlideEnabled", "songSettings.titleSlideVerticalAlignment", "songSettings.wordWrap",
    "songSettings.autoRepeatChorus", "songSettings.showEndOfSongIndicator",
    "songSettings.endOfSongIndicatorSpacing", SECTION_LABEL_PATH,
)

/** Where the Text group's rows store [element]'s look, on a linked profile; none elsewhere. */
@Composable
private fun songLookPaths(
    song: SongSettings,
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
): TextLookPaths {
    val link = LocalProfileLink.current?.takeIf { it.isLinked } ?: return TextLookPaths.NONE
    val base = link.profile.copy(songSettings = song)
    return remember(link.profile.id, element, target, language) {
        val style = song.elementStyle(element, target, language)
        probeTextLookPaths(base, style.toLook(element)) { p, look ->
            p.copy(songSettings = p.songSettings.withElementStyle(element, target, language, style.withLook(look)))
        }
    }
}
