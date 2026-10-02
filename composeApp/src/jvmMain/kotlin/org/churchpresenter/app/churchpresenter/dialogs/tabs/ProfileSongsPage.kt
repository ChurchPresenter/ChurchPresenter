package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bottom
import org.churchpresenter.strings.generated.resources.content_bible_translations_all
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.middle
import org.churchpresenter.strings.generated.resources.profile_end_marker
import org.churchpresenter.strings.generated.resources.profile_end_marker_spacing
import org.churchpresenter.strings.generated.resources.profile_fit_languages
import org.churchpresenter.strings.generated.resources.profile_fit_languages_each
import org.churchpresenter.strings.generated.resources.profile_fit_languages_same
import org.churchpresenter.strings.generated.resources.profile_fit_languages_sub
import org.churchpresenter.strings.generated.resources.profile_box_languages
import org.churchpresenter.strings.generated.resources.profile_group_slides
import org.churchpresenter.strings.generated.resources.profile_group_text
import org.churchpresenter.strings.generated.resources.profile_language_gap
import org.churchpresenter.strings.generated.resources.profile_language_gap_sub
import org.churchpresenter.strings.generated.resources.profile_layout
import org.churchpresenter.strings.generated.resources.unit_px
import org.churchpresenter.strings.generated.resources.profile_repeat_chorus
import org.churchpresenter.strings.generated.resources.profile_section_label
import org.churchpresenter.strings.generated.resources.profile_section_label_sub
import org.churchpresenter.strings.generated.resources.profile_position_above_lyrics
import org.churchpresenter.strings.generated.resources.profile_position_below_lyrics
import org.churchpresenter.strings.generated.resources.profile_position_bottom
import org.churchpresenter.strings.generated.resources.profile_position_top
import org.churchpresenter.strings.generated.resources.profile_slide_element
import org.churchpresenter.strings.generated.resources.profile_song_position
import org.churchpresenter.strings.generated.resources.profile_title_slide
import org.churchpresenter.strings.generated.resources.profile_title_slide_sub
import org.churchpresenter.strings.generated.resources.profile_title_slide_valign
import org.churchpresenter.strings.generated.resources.profile_word_wrap
import org.churchpresenter.strings.generated.resources.top
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.DEFAULT_STACKED_LANGUAGE_GAP
import org.churchpresenter.settings.LANGUAGE_GAP_RANGE
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
    /**
     * Where the Text rows point -- the title slide's element and the language, null for All. Held
     * by the editor, for the preview to pick.
     */
    targets: SongTargets,
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
        contentLabel = stringResource(Res.string.songs),
        draft = draft,
        profile = profile,
        onProfileChange = onProfileChange,
        onSettingsChange = onSettingsChange,
        onOpenBackground = { onOpenPage(ProfilePage.Appearance(CustomizePane.BACKGROUND)) },
    )
    SongTextGroup(
        draft,
        profile,
        element,
        onElementChange,
        updateSong,
        onSettingsChange,
        onProfileChange,
        targets,
    )
    if (profile.songMode == Constants.SONG_LANG_BOTH) {
        SettingsGroup(stringResource(Res.string.profile_box_languages), key = "languages", paths = SONG_LAYOUT_PATHS) {
            SettingsRow(stringResource(Res.string.profile_layout), paths = SONG_LAYOUT_PATHS) {
                RowSegmented(
                    options = bilingualLayoutRowOptions(),
                    selected = song.bilingualLayout,
                    onSelect = { v -> updateSong { it.copy(bilingualLayout = v) } },
                )
            }
            SettingsRow(
                stringResource(Res.string.profile_language_gap),
                sub = stringResource(Res.string.profile_language_gap_sub),
                paths = SONG_GAP_PATHS,
            ) {
                RowStepper(
                    value = song.layoutExtras.languageGap ?: DEFAULT_STACKED_LANGUAGE_GAP,
                    onValueChange = { v ->
                        updateSong { it.copy(layoutExtras = it.layoutExtras.copy(languageGap = v)) }
                    },
                    range = LANGUAGE_GAP_RANGE,
                    step = LANGUAGE_GAP_STEP,
                    unit = stringResource(Res.string.unit_px),
                    testTag = SONG_LANGUAGE_GAP_TAG,
                )
            }
            SettingsRow(
                stringResource(Res.string.profile_fit_languages),
                sub = stringResource(Res.string.profile_fit_languages_sub),
                advanced = true,
                paths = SONG_FIT_LANGUAGES_PATHS,
            ) {
                RowSegmented(
                    options = listOf(
                        RowOption(false, stringResource(Res.string.profile_fit_languages_same)),
                        RowOption(true, stringResource(Res.string.profile_fit_languages_each)),
                    ),
                    selected = song.layoutExtras.fitLanguagesSeparately,
                    onSelect = { v ->
                        updateSong { it.copy(layoutExtras = it.layoutExtras.copy(fitLanguagesSeparately = v)) }
                    },
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
 * The Text group: the strip picking the language and element, then everything about the element
 * itself -- what it shows, the song tab's own options for it, its label switch and its place -- and
 * only then its look.
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
    targets: SongTargets,
) {
    val lowerThird = profile.isLowerThird
    val target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
    val song = draft.songSettings
    val titleSlideView = element == CustomizeElement.SONG_TITLE_SLIDE
    val slideElement = targets.slideElement.value
    val styleElement = if (titleSlideView) slideElement else element.toSongStyleElement()
    val offered = songLanguagesOffered(profile, styleElement)
    val perLanguage = offered.isNotEmpty()
    val editingLanguage = targets.language.value?.takeIf { it in offered }
    val edit = SongEdit(song, styleElement, target, editingLanguage, perLanguage, updateSong)
    val style = edit.style
    val elements = styleElementsFor(CustomizePane.SONGS, profile)
    val lookPaths = songLookPaths(song, styleElement, target, editingLanguage ?: SongStyleLanguage.PRIMARY) +
        edit.targetPaths()
    val allLabel = stringResource(Res.string.content_bible_translations_all)
    SettingsGroup(
        caption = stringResource(Res.string.profile_group_text),
        key = "text",
        paths = lookPaths.all,
        action = ResetAction(edit.resettable) { edit.reset() },
        summary = { style.toLook(styleElement).let { textSummary(it.fontType, it.fontSize) } },
        header = {
            AppliesToStrip(
                targets = if (perLanguage) {
                    listOf(RowOption<SongStyleLanguage?>(null, allLabel, SONG_ALL_LANGUAGES_TAG)) +
                        offered.map { RowOption<SongStyleLanguage?>(it, it.nameLabel(song), songLanguageTag(it)) }
                } else {
                    emptyList()
                },
                target = editingLanguage,
                onTarget = targets.language.onChange,
                elements = elements.map { RowOption(it, it.label(), elementChipTag(it.name)) },
                element = element,
                onElement = onElementChange,
            )
        },
    ) {
        if (titleSlideView) SlideElementRow(slideElement, targets.slideElement.onChange)
        SongElementRow(draft, profile, styleElement, target, titleSlideView, onSettingsChange, onProfileChange)
        key(styleElement, editingLanguage) { CompositionLocalProvider(LocalStyleTarget provides edit.styleTarget()) {
            // One language picked: its own Auto-fit only means something while languages are
            // fitted one by one; fitted together, All's switch decides for every one of them.
            val ownFit = edit.picked && !song.layoutExtras.fitLanguagesSeparately
            // A boxed item is placed by its box and fitted by its box's own choice; its place, its
            // move and its Auto-fit switch stand aside until the box is turned off.
            val boxes = edit.boxTarget(lowerThird, titleSlideView)
            val boxed = boxes?.box?.enabled == true
            TextLookRows(
                look = style.toLook(styleElement).let { if (ownFit || boxed) it.copy(autoFit = null) else it },
                onChange = { look -> edit.write(style.withLook(look)) },
                extraAdvanced = {
                    if (!boxed) SongMoveRow(
                        song = song,
                        key = songShiftKey(styleElement, lowerThird, editingLanguage?.translation, titleSlideView),
                        language = editingLanguage != null,
                        updateSong = updateSong,
                    )
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
                leading = {
                    if (styleElement == SongStyleElement.SECTION_LABEL) {
                        SectionLabelSwitch(song.layoutExtras.sectionLabel.enabled, updateSong)
                    }
                    val stored = song.storedPosition(styleElement, lowerThird)
                    if (song.offersPosition(styleElement, lowerThird, titleSlideView || boxed)) {
                        SongPositionRow(
                            selected = stored ?: style.position,
                            onSelect = { v ->
                                if (stored != null) {
                                    updateSong { it.withStoredPosition(styleElement, lowerThird, v) }
                                } else {
                                    edit.write(style.copy(position = v))
                                }
                            },
                        )
                    }
                },
                extraBasic = { SongBoxRows(boxes, song, lowerThird, perLanguage, updateSong) },
            )
        } }
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
        room = MarginRoom.reference(song.lowerThirdHeightPercent.takeIf { lowerThird }),
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
            scope = BackgroundScope.SONG_LOWER_THIRD,
            prefix = "songSettings",
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
    val titleSlide = stringResource(Res.string.profile_title_slide)
    val wordWrap = stringResource(Res.string.profile_word_wrap)
    val repeatChorus = stringResource(Res.string.profile_repeat_chorus)
    SettingsGroup(
        stringResource(Res.string.profile_group_slides),
        key = "slides",
        paths = SLIDES_PATHS,
        // The switches that are on, by name.
        summary = {
            listOfNotNull(
                titleSlide.takeIf { song.titleSlideEnabled },
                wordWrap.takeIf { song.wordWrap },
                repeatChorus.takeIf { song.autoRepeatChorus },
            ).joinToString(" · ")
        },
    ) {
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
        SectionLabelSwitch(song.layoutExtras.sectionLabel.enabled, updateSong)
    }
}

/**
 * The section label on or off. On the Slides group, where it has always been, and at the top of the
 * Section Label element's own rows, where its look is.
 */
@Composable
private fun SectionLabelSwitch(enabled: Boolean, updateSong: ((SongSettings) -> SongSettings) -> Unit) {
    SettingsSwitchRow(
        stringResource(Res.string.profile_section_label),
        enabled,
        { v ->
            updateSong { s ->
                s.copy(layoutExtras = s.layoutExtras.copy(sectionLabel = s.layoutExtras.sectionLabel.copy(enabled = v)))
            }
        },
        sub = stringResource(Res.string.profile_section_label_sub),
        paths = listOf("$SECTION_LABEL_PATH.enabled"),
    )
}

/** POSITION: the content area's top edge, held above or below the lyrics, or the bottom edge. */
@Composable
private fun SongPositionRow(selected: String, onSelect: (String) -> Unit) {
    SettingsRow(stringResource(Res.string.profile_song_position)) {
        RowSegmented(
            options = listOf(
                RowOption(Constants.ABOVE_VERSE, stringResource(Res.string.profile_position_top)),
                RowOption(Constants.ABOVE_LYRICS, stringResource(Res.string.profile_position_above_lyrics)),
                RowOption(Constants.BELOW_LYRICS, stringResource(Res.string.profile_position_below_lyrics)),
                RowOption(Constants.BELOW_VERSE, stringResource(Res.string.profile_position_bottom)),
            ),
            selected = selected,
            onSelect = onSelect,
            compact = true,
        )
    }
}

/** Which stored profile a Songs element stands for. */
internal fun CustomizeElement.toSongStyleElement(): SongStyleElement = when (this) {
    CustomizeElement.SONG_TITLE -> SongStyleElement.TITLE
    CustomizeElement.SONG_NUMBER -> SongStyleElement.NUMBER
    CustomizeElement.SONG_LOOK_AHEAD -> SongStyleElement.LOOK_AHEAD
    CustomizeElement.SONG_NEXT_SECTION -> SongStyleElement.NEXT_SECTION
    CustomizeElement.SONG_SECTION_LABEL -> SongStyleElement.SECTION_LABEL
    else -> SongStyleElement.LYRICS
}

/** Test handle for one language of the Songs strip. */
internal fun songLanguageTag(language: SongStyleLanguage): String = "profile_song_language_${language.name}"

/** Test handle for All in the Songs strip. */
internal const val SONG_ALL_LANGUAGES_TAG = "profile_song_language_all"

/** Test handle for the lyrics block's own positioning switch. */
internal const val LYRICS_OFFSET_TAG = "song_lyrics_offset"

private val SONG_FIT_LANGUAGES_PATHS = listOf("songSettings.layoutExtras.fitLanguagesSeparately")

/** Where the languages' layout is stored. */
private val SONG_LAYOUT_PATHS =
    listOf("songSettings.bilingualLayout", "songSettings.layoutExtras.languageGap") + SONG_FIT_LANGUAGES_PATHS
private val SONG_GAP_PATHS = listOf("songSettings.layoutExtras.languageGap")
private const val LANGUAGE_GAP_STEP = 4

/** Test handle for the Gap between languages field. */
internal const val SONG_LANGUAGE_GAP_TAG = "profile_song_language_gap"

/** Where the section label is stored. */
private const val SECTION_LABEL_PATH = "songSettings.layoutExtras.sectionLabel"

/** Everything the Slides group writes. */
private val SLIDES_PATHS = listOf(
    "songSettings.titleSlideEnabled", "songSettings.titleSlideVerticalAlignment", "songSettings.wordWrap",
    "songSettings.autoRepeatChorus", "songSettings.showEndOfSongIndicator",
    "songSettings.endOfSongIndicatorSpacing", "$SECTION_LABEL_PATH.enabled",
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

