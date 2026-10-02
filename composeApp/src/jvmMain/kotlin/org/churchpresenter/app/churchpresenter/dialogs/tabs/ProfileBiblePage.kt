package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.content_bible_translations_all
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.customize_group_reference
import org.churchpresenter.strings.generated.resources.customize_group_verse_text
import org.churchpresenter.strings.generated.resources.customize_show_abbreviation
import org.churchpresenter.strings.generated.resources.pixels_short
import org.churchpresenter.strings.generated.resources.profile_group_text
import org.churchpresenter.strings.generated.resources.profile_group_translations
import org.churchpresenter.strings.generated.resources.profile_layout
import org.churchpresenter.strings.generated.resources.profile_ref_above
import org.churchpresenter.strings.generated.resources.profile_ref_after
import org.churchpresenter.strings.generated.resources.profile_reference
import org.churchpresenter.strings.generated.resources.profile_shift
import org.churchpresenter.strings.generated.resources.profile_shift_element_sub
import org.churchpresenter.strings.generated.resources.profile_shift_sub
import org.churchpresenter.strings.generated.resources.profile_space_between_translations
import org.churchpresenter.strings.generated.resources.bible_split_long_verses
import org.churchpresenter.strings.generated.resources.profile_split_words
import org.churchpresenter.strings.generated.resources.profile_translation_divider
import org.churchpresenter.strings.generated.resources.words_suffix
import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.app.churchpresenter.presenter.bibleBoxKey
import org.churchpresenter.app.churchpresenter.presenter.movedOn
import org.churchpresenter.app.churchpresenter.presenter.referenceShiftFor
import org.churchpresenter.app.churchpresenter.presenter.withMovesCleared
import org.churchpresenter.app.churchpresenter.presenter.withReferenceShift
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_MAX
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_MIN
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_STEP
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.allStyle
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.clearOwnStyle
import org.churchpresenter.settings.updateAllLayer
import org.churchpresenter.settings.updateOwnStyle
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withBox
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
    val edit = BibleEdit(draft.bibleSettings, profile, translationIndex, element, lowerThird, onSettingsChange)
    ContentBackgroundGroup(
        scope = if (lowerThird) BackgroundScope.BIBLE_LOWER_THIRD else BackgroundScope.BIBLE,
        contentLabel = stringResource(Res.string.bible),
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
internal class BibleEdit(
    val bs: BibleSettings,
    profile: OutputProfile,
    translationIndex: Int,
    element: CustomizeElement,
    val lowerThird: Boolean,
    private val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val stack = bs.translationList()

    /**
     * The stack positions this output draws, in its order -- the only translations worth offering
     * here. The whole stack when it draws none, so the page still has a translation to style.
     */
    val shownPositions = shownBiblePositions(profile, stack.size).ifEmpty { stack.indices.toList() }

    /** The translation picked, or All when the pick is one this output no longer draws. */
    val index = effectiveTranslationIndex(translationIndex, stack.size).let {
        if (it != ALL_TRANSLATIONS && stack.size > 1 && it !in shownPositions) ALL_TRANSLATIONS else it
    }
    val all = index == ALL_TRANSLATIONS
    val shown = if (all) bs.allStyle() else stack.getOrNull(index) ?: BibleTranslationSettings()
    val target = if (lowerThird) BibleStyleTarget.LOWER_THIRD else BibleStyleTarget.FULL_SCREEN
    val styleElement =
        if (element == CustomizeElement.BIBLE_REFERENCE) BibleStyleElement.REFERENCE else BibleStyleElement.TEXT
    val style = shown.elementStyle(styleElement, target)

    /** One translation of a stack of several is being edited, rather than All. */
    val picked = !all && stack.size > 1

    /** The picked translation as the rows mark its own values; null under All. */
    @Composable
    fun styleTarget(): StyleTarget? {
        val entry = stack.getOrNull(index)
        if (!picked || entry == null) return null
        val label = entry.customAbbreviation
            .ifBlank { defaultTranslationAbbreviation(title = "", fileName = entry.fileName) }
        return StyleTarget(label, "bibleSettings.translations[${entry.fileName}]", entry.ownStyleKeys, ::clearOwn)
    }

    /**
     * [transform] of the translation being edited, whose changed fields become its own -- or, under
     * All, of the All layer and every translation without a value of its own for what changed.
     */
    fun updateEntry(transform: (BibleTranslationSettings) -> BibleTranslationSettings) {
        onSettingsChange { s -> s.copy(bibleSettings = entryUpdated(s.bibleSettings, transform)) }
    }

    private fun entryUpdated(
        bible: BibleSettings,
        transform: (BibleTranslationSettings) -> BibleTranslationSettings,
    ): BibleSettings = if (all) bible.updateAllLayer(transform) else bible.updateOwnStyle(index, transform)

    /** Translation [index] following All again at [keys] -- its "Only KJV ×". */
    fun clearOwn(keys: Collection<String>) {
        onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.clearOwnStyle(index, keys)) }
    }

    /** [transform] of the settings that are one for the whole Bible rather than per translation. */
    fun updateBible(transform: (BibleSettings) -> BibleSettings) {
        onSettingsChange { s -> s.copy(bibleSettings = transform(s.bibleSettings)) }
    }

    /** [edited] written to [of] of the translation being edited, or of the All layer. */
    fun writeStyle(edited: BibleElementStyle, of: BibleStyleElement = styleElement) {
        onSettingsChange { s -> s.copy(bibleSettings = styled(s.bibleSettings, edited, of)) }
    }

    /** The reference's own move on this output, where the Text rows point at the reference; none otherwise. */
    val referenceShift: Pair<Int, Int>
        get() = if (styleElement == BibleStyleElement.REFERENCE) shown.referenceShiftFor(lowerThird) else 0 to 0

    /**
     * Reset: [defaults] written as [writeStyle] writes them and, on the reference, its own move taken
     * back too -- in one write, since a reference moved out of sight has no handle left to drag back.
     */
    fun reset(defaults: BibleElementStyle) {
        val clearShift = styleElement == BibleStyleElement.REFERENCE
        onSettingsChange { s ->
            val styled = styled(s.bibleSettings, defaults)
            s.copy(
                bibleSettings = if (clearShift) {
                    entryUpdated(styled) { it.withReferenceShift(lowerThird, 0, 0) }
                } else {
                    styled
                },
            )
        }
    }

    /** [bible] with [edited] written the way [writeStyle] writes it. */
    fun styled(bible: BibleSettings, edited: BibleElementStyle, of: BibleStyleElement = styleElement): BibleSettings =
        entryUpdated(bible) { it.withElementStyle(of, target, edited) }

    /**
     * Where the Text group's rows store their values -- for a linked profile to mark them, and for a
     * picked translation to show which are its own. None when neither needs them.
     */
    @Composable
    fun lookPaths(): TextLookPaths {
        val link = LocalProfileLink.current ?: return TextLookPaths.NONE
        if (!link.isLinked && !picked) return TextLookPaths.NONE
        val base = link.profile.copy(bibleSettings = bs)
        return remember(link.profile.id, index, styleElement, target, stack.map { it.fileName }) {
            probeTextLookPaths(base, style.toLook()) { p, look ->
                p.copy(bibleSettings = styled(p.bibleSettings, style.withLook(look)))
            }
        }
    }
}

/** TEXT: the "Applies to" strip, the reference's abbreviation -- the element's own -- then its look. */
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
        key = "text",
        paths = lookPaths.all,
        summary = { style.toLook().let { textSummary(it.fontType, it.fontSize) } },
        action = ResetAction(style != defaults || edit.referenceShift != (0 to 0)) { edit.reset(defaults) },
        header = {
            AppliesToStrip(
                targets = bibleTargets(edit.stack, edit.shownPositions),
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
        key(edit.index, edit.styleElement) { CompositionLocalProvider(LocalStyleTarget provides edit.styleTarget()) {
            val boxes = edit.boxTarget()
            // A boxed element is placed by its box; its shift stands aside until the box is off.
            val boxed = boxes?.box?.enabled == true
            TextLookRows(
                look = style.toLook(),
                onChange = { look -> edit.writeStyle(style.withLook(look)) },
                fonts = rememberSystemFonts(),
                paths = lookPaths,
                leading = {
                    if (edit.styleElement == BibleStyleElement.REFERENCE) {
                        SettingsSwitchRow(
                            stringResource(Res.string.customize_show_abbreviation),
                            edit.shown.showAbbreviation,
                            { v -> edit.updateEntry { it.copy(showAbbreviation = v) } },
                        )
                    }
                },
                extraBasic = { BibleBoxRows(boxes, edit) },
                extraAdvanced = {
                    when {
                        boxed -> Unit
                        edit.styleElement == BibleStyleElement.REFERENCE -> ReferenceShiftRow(edit)
                        edit.picked -> ShiftRow(edit)
                    }
                },
            )
        } }
    }
}

/** SHIFT X / Y: the picked translation's block moved on its own, in output pixels. */
@Composable
private fun ShiftRow(edit: BibleEdit) {
    val entry = edit.shown
    val lowerThird = edit.lowerThird
    val x = if (lowerThird) entry.lowerThirdShiftX else entry.shiftX
    val y = if (lowerThird) entry.lowerThirdShiftY else entry.shiftY
    val entryPath = "bibleSettings.translations[${entry.fileName}]"
    val fields = if (lowerThird) listOf("lowerThirdShiftX", "lowerThirdShiftY") else listOf("shiftX", "shiftY")
    SettingsRow(
        stringResource(Res.string.profile_shift),
        sub = stringResource(Res.string.profile_shift_sub),
        advanced = true,
        paths = fields.map { "$entryPath.$it" },
    ) {
        val px = stringResource(Res.string.pixels_short)
        RowNumberField(
            x,
            { v -> edit.updateEntry { if (lowerThird) it.copy(lowerThirdShiftX = v) else it.copy(shiftX = v) } },
            SHIFT_RANGE,
            unit = "X $px",
            width = 72.dp,
            testTag = SHIFT_X_TAG,
        )
        RowNumberField(
            y,
            { v -> edit.updateEntry { if (lowerThird) it.copy(lowerThirdShiftY = v) else it.copy(shiftY = v) } },
            SHIFT_RANGE,
            unit = "Y $px",
            width = 72.dp,
            testTag = SHIFT_Y_TAG,
        )
    }
}

/**
 * MOVE X / Y for the reference: moved on its own, on top of its translation's block -- the picked
 * translation's, or under All every translation's that has no move of its own.
 */
@Composable
private fun ReferenceShiftRow(edit: BibleEdit) {
    val lowerThird = edit.lowerThird
    val (x, y) = edit.shown.referenceShiftFor(lowerThird)
    val fields = if (lowerThird) {
        listOf("lowerThirdReferenceShiftX", "lowerThirdReferenceShiftY")
    } else {
        listOf("referenceShiftX", "referenceShiftY")
    }
    val entryPath = "bibleSettings.translations[${edit.shown.fileName}]"
    SettingsRow(
        stringResource(Res.string.profile_shift),
        sub = stringResource(Res.string.profile_shift_element_sub),
        advanced = true,
        paths = if (edit.picked) fields.map { "$entryPath.$it" } else emptyList(),
    ) {
        val px = stringResource(Res.string.pixels_short)
        RowNumberField(
            x,
            { v -> edit.updateEntry { it.withReferenceShift(lowerThird, v, y) } },
            SHIFT_RANGE,
            unit = "X $px",
            width = 72.dp,
            testTag = SHIFT_X_TAG,
        )
        RowNumberField(
            y,
            { v -> edit.updateEntry { it.withReferenceShift(lowerThird, x, v) } },
            SHIFT_RANGE,
            unit = "Y $px",
            width = 72.dp,
            testTag = SHIFT_Y_TAG,
        )
    }
}

private val SHIFT_RANGE = -960..960

/** Test handles for the Shift X / Y fields. */
internal const val SHIFT_X_TAG = "profile_shift_x"
internal const val SHIFT_Y_TAG = "profile_shift_y"

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
        room = MarginRoom.reference(bs.lowerThirdHeightPercent.takeIf { edit.lowerThird }),
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
    )
    if (edit.lowerThird) {
        BandGroup(
            scope = BackgroundScope.BIBLE_LOWER_THIRD,
            prefix = "bibleSettings",
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

/**
 * All, then each translation this output draws, in its order -- the rest of the stack is not on
 * this screen, so there is nothing of theirs to see here. Each keeps its stack position as its value.
 */
@Composable
private fun bibleTargets(stack: List<BibleTranslationSettings>, shown: List<Int>): List<RowOption<Int>> =
    listOf(
        RowOption(
            ALL_TRANSLATIONS,
            stringResource(Res.string.content_bible_translations_all),
            translationChipTag(ALL_TRANSLATIONS),
        ),
    ) + shown.mapNotNull { index -> stack.getOrNull(index)?.let { index to it } }.map { (index, translation) ->
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
private fun TranslationsGroup(edit: BibleEdit, profile: OutputProfile) {
    val bs = edit.bs
    val stack = edit.stack
    val lowerThird = edit.lowerThird
    val updateBible = edit::updateBible
    val parallel = profile.bibleMode != Constants.SONG_LANG_OFF &&
        (profile.bibleTranslations.size > 1 || profile.bibleTranslations.isEmpty()) &&
        stack.size > 1
    val d = BibleSettings()
    SettingsGroup(
        caption = stringResource(Res.string.profile_group_translations), key = "translations",
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
            stringResource(Res.string.bible_split_long_verses),
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

/** The Adjust handles on the Bible page: its margins and block, and the text its Text rows are pointed at. */
internal fun bibleAdjustModel(
    draft: AppSettings,
    profile: OutputProfile,
    /** Which element the Text rows edit, the verse or its reference; the preview can pick the other. */
    element: Adjustable<CustomizeElement>,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** Which translation the Text rows edit, All included; the preview can pick another. */
    translation: Adjustable<Int>,
): AdjustModel {
    val onElementChange = element.onChange
    val edit = BibleEdit(
        draft.bibleSettings,
        profile,
        translation.value,
        element.value,
        profile.isLowerThird,
        onSettingsChange,
    )
    val bs = edit.bs
    val margins = Margins(bs.marginTop, bs.marginBottom, bs.marginLeft, bs.marginRight)
    return AdjustModel(
        margins = Adjustable(margins) { m ->
            edit.updateBible {
                it.copy(marginTop = m.top, marginBottom = m.bottom, marginLeft = m.left, marginRight = m.right)
            }
        },
        alignment = Adjustable(bs.verticalAlignment) { a ->
            edit.updateBible {
                it.copy(verticalAlignment = a, contentRegion = it.contentRegion.copy(yOffsetPercent = 0))
            }
        },
        region = if (edit.lowerThird) {
            null
        } else {
            Adjustable(bs.contentRegion) { r -> edit.updateBible { it.copy(contentRegion = r) } }
        },
        textSize = Adjustable(edit.style.fontSize) { v -> edit.writeStyle(edit.style.copy(fontSize = v)) },
        band = if (edit.lowerThird) {
            Adjustable(bs.lowerThirdHeightPercent) { v -> edit.updateBible { it.copy(lowerThirdHeightPercent = v) } }
        } else {
            null
        },
        blocks = bibleBlocks(edit, translation.onChange, onElementChange),
        positions = PositionsReset(
            moved = (bs.translations + listOfNotNull(bs.allTranslationStyle)).any { it.movedOn(edit.lowerThird) } ||
                bs.textBoxes.any { (key, box) ->
                    box.enabled && key.endsWith(LOWER_THIRD_BOX_SUFFIX) == edit.lowerThird
                },
        ) {
            val lowerThird = edit.lowerThird
            onSettingsChange { s ->
                val bible = s.bibleSettings
                s.copy(
                    bibleSettings = bible.copy(
                        translations = bible.translations.map { it.withMovesCleared(lowerThird) },
                        allTranslationStyle = bible.allTranslationStyle?.withMovesCleared(lowerThird),
                        // Boxes are turned off, not forgotten: turning one back on puts it where it was.
                        textBoxes = bible.textBoxes.mapValues { (key, box) ->
                            if (key.endsWith(LOWER_THIRD_BOX_SUFFIX) == lowerThird) box.copy(enabled = false) else box
                        },
                    ),
                )
            }
        },
        boxes = bibleBoxTargets(edit, translation.onChange, onElementChange),
    )
}

/** The suffix a lower third's box keys carry -- see `textBoxKey`. */
private const val LOWER_THIRD_BOX_SUFFIX = "@LT"

/**
 * Each shown translation's verse and reference boxes on this output, one handle each -- one in all
 * where the translations share a box -- and the one the Text rows point at; null while none is on.
 */
private fun bibleBoxTargets(
    edit: BibleEdit,
    onTranslationChange: (Int) -> Unit,
    onElementChange: (CustomizeElement) -> Unit,
): BoxTargets? {
    val bs = edit.bs
    val shown = edit.shownPositions.filter { it in edit.stack.indices }
    val handles = shown.flatMap { position ->
        val fileName = edit.stack[position].fileName
        BibleStyleElement.entries.mapNotNull { element ->
            val key = bs.bibleBoxKey(element, edit.lowerThird, fileName)
            val box = bs.textBoxes.boxAt(key)
            if (!box.enabled) return@mapNotNull null
            BoxHandle(
                key = key,
                box = box,
                onChange = { changed -> edit.updateBible { it.copy(textBoxes = it.textBoxes.withBox(key, changed)) } },
                onPick = {
                    onTranslationChange(position)
                    onElementChange(
                        if (element == BibleStyleElement.REFERENCE) {
                            CustomizeElement.BIBLE_REFERENCE
                        } else {
                            CustomizeElement.BIBLE_TEXT
                        },
                    )
                },
            )
        }
    }.distinctBy { it.key }
    if (handles.isEmpty()) return null
    return BoxTargets(handles, edit.boxTarget()?.key, bs.textBoxOptions)
}

/**
 * Each translation this output draws as a block to pick and move, and the picked (or first) one's
 * reference as a block of its own, moved anywhere -- under All, every translation's reference.
 */
private fun bibleBlocks(
    edit: BibleEdit,
    onTranslationChange: (Int) -> Unit,
    onElementChange: (CustomizeElement) -> Unit,
): BlockTargets {
    val lowerThird = edit.lowerThird
    val shown = edit.shownPositions.filter { it in edit.stack.indices }
    val referenceEntry = edit.shown
    return BlockTargets(
        kind = PresentedBlock.Kind.TRANSLATION,
        keys = shown.map { edit.stack[it].fileName },
        selected = shown.indexOf(edit.index).takeIf { edit.picked && it >= 0 },
        onSelect = { i ->
            onTranslationChange(shown[i])
            onElementChange(CustomizeElement.BIBLE_TEXT)
        },
        shift = if (edit.picked) {
            val entry = edit.shown
            val now = if (lowerThird) entry.lowerThirdShiftX to entry.lowerThirdShiftY else entry.shiftX to entry.shiftY
            Adjustable(now) { (x, y) ->
                edit.updateEntry {
                    if (lowerThird) it.copy(
                        lowerThirdShiftX = x,
                        lowerThirdShiftY = y,
                    ) else it.copy(shiftX = x, shiftY = y)
                }
            }
        } else {
            null
        },
        reference = ReferenceTarget(
            shift = Adjustable(referenceEntry.referenceShiftFor(lowerThird)) { (x, y) ->
                edit.updateEntry { it.withReferenceShift(lowerThird, x, y) }
            },
            picked = edit.styleElement == BibleStyleElement.REFERENCE,
            onPick = { onElementChange(CustomizeElement.BIBLE_REFERENCE) },
        ),
    )
}
