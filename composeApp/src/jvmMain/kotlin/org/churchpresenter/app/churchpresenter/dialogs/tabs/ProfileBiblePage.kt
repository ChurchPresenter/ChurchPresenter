package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.content_bible_translations_all
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_group_reference
import churchpresenter.composeapp.generated.resources.customize_group_verse_text
import churchpresenter.composeapp.generated.resources.customize_show_abbreviation
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.profile_group_text
import churchpresenter.composeapp.generated.resources.profile_group_translations
import churchpresenter.composeapp.generated.resources.profile_layout
import churchpresenter.composeapp.generated.resources.profile_ref_above
import churchpresenter.composeapp.generated.resources.profile_ref_after
import churchpresenter.composeapp.generated.resources.profile_reference
import churchpresenter.composeapp.generated.resources.profile_space_between_translations
import churchpresenter.composeapp.generated.resources.profile_split_long_verses
import churchpresenter.composeapp.generated.resources.profile_split_words
import churchpresenter.composeapp.generated.resources.profile_translation_divider
import churchpresenter.composeapp.generated.resources.words_suffix
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_MAX
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_MIN
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_STEP
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The Bible page: its background, the verse text and reference, how parallel translations sit
 * together, where the block sits, the lower-third band and the fades -- one group each.
 *
 * The Text group's "Applies to" strip picks which translation of the stack the rows edit, and
 * which element. Under All an edit writes only the property that changed to every translation, so
 * each keeps whatever else it has of its own -- the rule the pane this replaced followed.
 */
@Composable
internal fun ProfileBiblePage(
    draft: AppSettings,
    profile: OutputProfile,
    translationIndex: Int,
    onTranslationChange: (Int) -> Unit,
    element: CustomizeElement,
    onElementChange: (CustomizeElement) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
    onOpenPage: (ProfilePage) -> Unit,
) {
    val lowerThird = profile.isLowerThird
    val edit = BibleEdit(draft.bibleSettings, translationIndex, element, lowerThird, onSettingsChange)
    ContentBackgroundGroup(
        scope = if (lowerThird) BackgroundScope.BIBLE_LOWER_THIRD else BackgroundScope.BIBLE,
        contentLabel = stringResource(Res.string.customize_bible),
        draft = draft,
        profile = profile,
        onProfileChange = onProfileChange,
        onSettingsChange = onSettingsChange,
        onOpenBackground = { onOpenPage(ProfilePage.Appearance(CustomizePane.BACKGROUND)) },
    )
    BibleTextGroup(edit, element, onTranslationChange, onElementChange)
    TranslationsGroup(edit, profile)
    BiblePlacementGroups(edit, draft, profile, onProfileChange, onSettingsChange)
}

/**
 * What the Bible page is pointed at -- one translation of the stack, or All, and one element on
 * this output's shape -- and the three ways it writes.
 *
 * A class rather than local functions in the page: a reference to a local function handed to a
 * child composable is remembered across compositions, and would keep writing through the document
 * as it was when the page was first drawn. A new instance each composition cannot.
 */
private class BibleEdit(
    val bs: BibleSettings,
    translationIndex: Int,
    element: CustomizeElement,
    val lowerThird: Boolean,
    private val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val stack = bs.translationList()
    val index = effectiveTranslationIndex(translationIndex, stack.size)
    val all = index == ALL_TRANSLATIONS
    val shown = stack.getOrNull(if (all) 0 else index) ?: BibleTranslationSettings()
    val target = if (lowerThird) BibleStyleTarget.LOWER_THIRD else BibleStyleTarget.FULL_SCREEN
    val styleElement =
        if (element == CustomizeElement.BIBLE_REFERENCE) BibleStyleElement.REFERENCE else BibleStyleElement.TEXT
    val style = shown.elementStyle(styleElement, target)

    /** [transform] of the translation being edited -- or of every one, under All. */
    fun updateEntry(transform: (BibleTranslationSettings) -> BibleTranslationSettings) {
        onSettingsChange { s ->
            val bible = s.bibleSettings
            s.copy(
                bibleSettings = if (all) {
                    bible.updateEveryTranslation(transform)
                } else {
                    bible.updateTranslation(index, transform)
                },
            )
        }
    }

    /** [transform] of the settings that are one for the whole Bible rather than per translation. */
    fun updateBible(transform: (BibleSettings) -> BibleSettings) {
        onSettingsChange { s -> s.copy(bibleSettings = transform(s.bibleSettings)) }
    }

    /**
     * [edited] written to [of] -- under All only the property that changed, so each translation
     * keeps everything else it had of its own.
     */
    fun writeStyle(edited: BibleElementStyle, of: BibleStyleElement = styleElement) {
        onSettingsChange { s -> s.copy(bibleSettings = styled(s.bibleSettings, edited, of)) }
    }

    /** [bible] with [edited] written the way [writeStyle] writes it. */
    fun styled(bible: BibleSettings, edited: BibleElementStyle, of: BibleStyleElement = styleElement): BibleSettings {
        val before = shown.elementStyle(of, target)
        val write = { t: BibleTranslationSettings ->
            val next = if (all) t.elementStyle(of, target).withChangesFrom(before, edited) else edited
            t.withElementStyle(of, target, next)
        }
        return if (all) bible.updateEveryTranslation(write) else bible.updateTranslation(index, write)
    }

    /** Where the Text group's rows store their values, on a linked profile; none elsewhere. */
    @Composable
    fun lookPaths(): TextLookPaths {
        val link = LocalProfileLink.current?.takeIf { it.isLinked } ?: return TextLookPaths.NONE
        val base = link.profile.copy(bibleSettings = bs)
        return remember(link.profile.id, index, styleElement, target, stack.map { it.fileName }) {
            probeTextLookPaths(base, style.toLook()) { p, look ->
                p.copy(bibleSettings = styled(p.bibleSettings, style.withLook(look)))
            }
        }
    }
}

/** TEXT: the "Applies to" strip, then the element's look, and the reference's abbreviation. */
@Composable
private fun BibleTextGroup(
    edit: BibleEdit,
    element: CustomizeElement,
    onTranslationChange: (Int) -> Unit,
    onElementChange: (CustomizeElement) -> Unit,
) {
    val style = edit.style
    val defaults = defaultElementStyle(edit.styleElement, edit.target)
    val lookPaths = edit.lookPaths()
    SettingsGroup(
        caption = stringResource(Res.string.profile_group_text),
        paths = lookPaths.all,
        action = ResetAction(style.copy(offset = null) != defaults.copy(offset = null)) {
            edit.writeStyle(defaults.copy(offset = style.offset))
        },
        header = {
            AppliesToStrip(
                targets = bibleTargets(edit.stack),
                target = edit.index,
                onTarget = onTranslationChange,
                elements = listOf(
                    RowOption(
                        CustomizeElement.BIBLE_TEXT,
                        stringResource(Res.string.customize_group_verse_text),
                        elementChipTag(CustomizeElement.BIBLE_TEXT.name),
                    ),
                    RowOption(
                        CustomizeElement.BIBLE_REFERENCE,
                        stringResource(Res.string.customize_group_reference),
                        elementChipTag(CustomizeElement.BIBLE_REFERENCE.name),
                    ),
                ),
                element = element,
                onElement = onElementChange,
            )
        },
    ) {
        // Keyed on what the rows point at: one set of controls stands for many stored styles, and
        // without this a field keeps the text it was typing into the translation it left.
        key(edit.index, edit.styleElement) {
            TextLookRows(
                look = style.toLook(),
                onChange = { look -> edit.writeStyle(style.withLook(look)) },
                fonts = rememberSystemFonts(),
                paths = lookPaths,
                extraBasic = {
                    if (edit.styleElement == BibleStyleElement.REFERENCE) {
                        SettingsSwitchRow(
                            stringResource(Res.string.customize_show_abbreviation),
                            edit.shown.showAbbreviation,
                            { v -> edit.updateEntry { it.copy(showAbbreviation = v) } },
                        )
                    }
                },
            )
        }
    }
}

/** Where the block sits, the band, and the fades. */
@Composable
private fun BiblePlacementGroups(
    edit: BibleEdit,
    draft: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val bs = edit.bs
    val d = BibleSettings()
    PositionGroup(
        paths = PositionPaths(
            vertical = listOf("bibleSettings.verticalAlignment"),
            margins = listOf("marginTop", "marginBottom", "marginLeft", "marginRight").map { "bibleSettings.$it" },
            region = listOf("bibleSettings.contentRegion"),
        ),
        verticalAlignment = bs.verticalAlignment,
        onVerticalAlignment = { v -> edit.updateBible { it.copy(verticalAlignment = v) } },
        margins = Margins(bs.marginTop, bs.marginBottom, bs.marginLeft, bs.marginRight),
        onMargins = { m ->
            edit.updateBible {
                it.copy(marginTop = m.top, marginBottom = m.bottom, marginLeft = m.left, marginRight = m.right)
            }
        },
        // Full screen only: a lower third's own width already is the band.
        region = bs.contentRegion.takeIf { !edit.lowerThird },
        onRegion = { r -> edit.updateBible { it.copy(contentRegion = r) } },
        reset = ResetAction(
            bs.verticalAlignment != d.verticalAlignment || bs.marginTop != d.marginTop ||
                bs.marginBottom != d.marginBottom || bs.marginLeft != d.marginLeft ||
                bs.marginRight != d.marginRight || bs.contentRegion != d.contentRegion,
        ) {
            edit.updateBible {
                it.copy(
                    verticalAlignment = d.verticalAlignment,
                    marginTop = d.marginTop,
                    marginBottom = d.marginBottom,
                    marginLeft = d.marginLeft,
                    marginRight = d.marginRight,
                    contentRegion = d.contentRegion,
                )
            }
        },
        extraAdvanced = {
            key(edit.index, edit.styleElement) {
                ElementPlacementRows(
                    offset = edit.style.offset,
                    onChange = { v -> edit.writeStyle(edit.style.copy(offset = v)) },
                    tagPrefix = bibleOffsetTag(edit.styleElement),
                )
            }
        },
    )
    if (edit.lowerThird) {
        BandGroup(
            prefix = "bibleSettings",
            scope = BackgroundScope.BIBLE_LOWER_THIRD,
            heightPercent = bs.lowerThirdHeightPercent,
            onHeight = { v -> edit.updateBible { it.copy(lowerThirdHeightPercent = v) } },
            draft = draft,
            profile = profile,
            onProfileChange = onProfileChange,
            onSettingsChange = onSettingsChange,
            reset = ResetAction(bs.lowerThirdHeightPercent != d.lowerThirdHeightPercent) {
                edit.updateBible { it.copy(lowerThirdHeightPercent = d.lowerThirdHeightPercent) }
            },
        )
    }
    TransitionGroup(
        prefix = "bibleSettings",
        fadeIn = bs.fadeIn,
        fadeOut = bs.fadeOut,
        crossfade = bs.crossfade,
        durationMs = bs.transitionDuration,
        onFadeIn = { v -> edit.updateBible { it.copy(fadeIn = v) } },
        onFadeOut = { v -> edit.updateBible { it.copy(fadeOut = v) } },
        onCrossfade = { v -> edit.updateBible { it.copy(crossfade = v) } },
        onDuration = { v -> edit.updateBible { it.copy(transitionDuration = v) } },
        reset = ResetAction(
            bs.fadeIn != d.fadeIn || bs.fadeOut != d.fadeOut || bs.crossfade != d.crossfade ||
                bs.transitionDuration != d.transitionDuration,
        ) {
            edit.updateBible {
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

/** All, then each translation of the stack as `1 · KJV`, numbered as the Bible tab numbers them. */
@Composable
private fun bibleTargets(stack: List<BibleTranslationSettings>): List<RowOption<Int>> =
    listOf(
        RowOption(
            ALL_TRANSLATIONS,
            stringResource(Res.string.content_bible_translations_all),
            translationChipTag(ALL_TRANSLATIONS),
        ),
    ) + stack.mapIndexed { index, translation ->
        val abbreviation = translation.customAbbreviation.ifBlank {
            defaultTranslationAbbreviation(title = "", fileName = translation.fileName)
        }
        RowOption(index, abbreviation, translationChipTag(index))
    }

private val SPACING_RANGE = -20..160

/** Everything the Translations group writes. */
private val TRANSLATIONS_PATHS = listOf(
    "bilingualLayout", "bilingualLayoutLowerThird", "multiTranslationDivider", "multiTranslationSpacing",
    "splitLongVerses", "longVerseWordCount",
).map { "bibleSettings.$it" }
private const val SPACING_STEP = 4

/**
 * TRANSLATIONS: how parallel translations are laid out against each other and where the reference
 * sits, then -- Advanced -- the divider, the gap between them and splitting a long verse.
 *
 * The layout is only offered where two translations can actually reach this output.
 */
@Composable
private fun TranslationsGroup(
    edit: BibleEdit,
    profile: OutputProfile,
) {
    val bs = edit.bs
    val stack = edit.stack
    val lowerThird = edit.lowerThird
    val updateBible = edit::updateBible
    val parallel = profile.bibleMode != Constants.SONG_LANG_OFF &&
        (profile.bibleTranslations.size > 1 || profile.bibleTranslations.isEmpty()) &&
        stack.size > 1
    val d = BibleSettings()
    SettingsGroup(
        caption = stringResource(Res.string.profile_group_translations),
        paths = TRANSLATIONS_PATHS,
        action = ResetAction(
            bs.bilingualLayout != d.bilingualLayout || bs.bilingualLayoutLowerThird != d.bilingualLayoutLowerThird ||
                bs.multiTranslationDivider != d.multiTranslationDivider ||
                bs.multiTranslationSpacing != d.multiTranslationSpacing || bs.splitLongVerses != d.splitLongVerses,
        ) {
            updateBible {
                it.copy(
                    bilingualLayout = d.bilingualLayout,
                    bilingualLayoutLowerThird = d.bilingualLayoutLowerThird,
                    multiTranslationDivider = d.multiTranslationDivider,
                    multiTranslationSpacing = d.multiTranslationSpacing,
                    splitLongVerses = d.splitLongVerses,
                    longVerseWordCount = d.longVerseWordCount,
                )
            }
        },
    ) {
        if (parallel) {
            SettingsRow(
                stringResource(Res.string.profile_layout),
                paths = listOf(
                    if (lowerThird) "bibleSettings.bilingualLayoutLowerThird" else "bibleSettings.bilingualLayout",
                ),
            ) {
                RowSegmented(
                    options = bilingualLayoutRowOptions(),
                    selected = if (lowerThird) bs.bilingualLayoutLowerThird else bs.bilingualLayout,
                    onSelect = { v ->
                        updateBible {
                            if (lowerThird) it.copy(bilingualLayoutLowerThird = v) else it.copy(bilingualLayout = v)
                        }
                    },
                )
            }
        }
        val reference = edit.shown.elementStyle(BibleStyleElement.REFERENCE, edit.target)
        SettingsRow(stringResource(Res.string.profile_reference)) {
            RowSegmented(
                options = listOf(
                    RowOption(Constants.POSITION_BELOW, stringResource(Res.string.profile_ref_after)),
                    RowOption(Constants.POSITION_ABOVE, stringResource(Res.string.profile_ref_above)),
                ),
                selected = reference.position,
                onSelect = { v -> edit.writeStyle(reference.copy(position = v), BibleStyleElement.REFERENCE) },
            )
        }
        // Offered whatever reaches this output, as the strip they came from did: they are set once
        // for the profile and take effect as soon as a second translation is shown.
        SettingsSwitchRow(
            stringResource(Res.string.profile_translation_divider),
            bs.multiTranslationDivider,
            { v -> updateBible { it.copy(multiTranslationDivider = v) } },
            advanced = true,
            paths = listOf("bibleSettings.multiTranslationDivider"),
        )
        SettingsRow(
            stringResource(Res.string.profile_space_between_translations),
            advanced = true,
            paths = listOf("bibleSettings.multiTranslationSpacing"),
        ) {
            RowStepper(
                bs.multiTranslationSpacing,
                { v -> updateBible { it.copy(multiTranslationSpacing = v) } },
                SPACING_RANGE,
                step = SPACING_STEP,
                unit = stringResource(Res.string.pixels_short),
            )
        }
        SettingsSwitchRow(
            stringResource(Res.string.profile_split_long_verses),
            bs.splitLongVerses,
            { v -> updateBible { it.copy(splitLongVerses = v) } },
            sub = if (bs.splitLongVerses) stringResource(Res.string.profile_split_words) else null,
            advanced = true,
            paths = listOf("bibleSettings.splitLongVerses", "bibleSettings.longVerseWordCount"),
            extra = {
                if (bs.splitLongVerses) {
                    RowStepper(
                        bs.longVerseWordCount,
                        { v -> updateBible { it.copy(longVerseWordCount = v) } },
                        LONG_VERSE_WORDS_MIN..LONG_VERSE_WORDS_MAX,
                        step = LONG_VERSE_WORDS_STEP,
                        unit = stringResource(Res.string.words_suffix),
                    )
                }
            },
        )
    }
}

/** Test handle for one Bible element's positioning switch. */
internal fun bibleOffsetTag(element: BibleStyleElement): String =
    if (element == BibleStyleElement.REFERENCE) "bible_reference_offset" else "bible_text_offset"
