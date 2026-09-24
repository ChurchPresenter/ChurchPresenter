package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.utils.Constants
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_show_abbreviation
import org.jetbrains.compose.resources.stringResource

/**
 * The Bible pane, showing one element of one translation -- the chips above it pick both.
 *
 * **One translation at a time, not the whole stack.** An earlier version of this pane read the
 * first translation's values and wrote every edit to all of them, so a screen could not be given a
 * smaller secondary language or a different colour for its third: the controls showed translation
 * one and silently overwrote the rest. The stack is ordered and each entry carries its own full
 * profile -- the same four families the global tab edits -- so the dialog offers the same choice,
 * per output.
 *
 * The margins, the fades and the band's geometry are not here: they belong to the picture rather
 * than to the verse text or its reference, and they sit under the preview in
 * [CustomizeCategoryStrip] where the picture they move is in the same glance.
 */
@Composable
internal fun BibleCustomizePane(
    element: CustomizeElement,
    /** Which entry of the ordered stack is being styled, or [ALL_TRANSLATIONS] -- see [CustomizeTranslationChips]. */
    translationIndex: Int,
    settings: AppSettings,
    /** This profile's own Bible selection, which decides whether an arrangement control applies. */
    profile: OutputProfile,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val scope = LocalOutputStyleScope.current
    val lowerThird = scope == OutputStyleScope.LOWER_THIRD
    val fonts = rememberSystemFonts()
    val bs = settings.bibleSettings
    val stack = bs.translationList()
    // A shelf with nothing configured still has a Bible style to edit, and an index left over from
    // a longer stack must not read off the end. Under All the controls show the first translation.
    val index = effectiveTranslationIndex(translationIndex, stack.size)
    val all = index == ALL_TRANSLATIONS
    val t = stack.getOrNull(if (all) 0 else index) ?: BibleTranslationSettings()

    // Writes the selected entry alone, through the same `updateTranslation` the global tab uses,
    // so both edit the stack by one path -- or, under All, every entry by that same path.
    fun updateEntry(transform: (BibleTranslationSettings) -> BibleTranslationSettings) {
        onSettingsChange { s ->
            val bible = s.bibleSettings
            s.copy(
                bibleSettings = if (all) bible.updateEveryTranslation(transform)
                else bible.updateTranslation(index, transform),
            )
        }
    }

    fun updateBible(transform: (BibleSettings) -> BibleSettings) {
        onSettingsChange { s -> s.copy(bibleSettings = transform(s.bibleSettings)) }
    }

    val target = if (lowerThird) BibleStyleTarget.LOWER_THIRD else BibleStyleTarget.FULL_SCREEN
    val styleElement =
        if (element == CustomizeElement.BIBLE_REFERENCE) BibleStyleElement.REFERENCE else BibleStyleElement.TEXT

    PaneScaffold {
        // The same panel the Bible settings tab draws, over the same profile. Its header is off:
        // the element and the translation are chosen by the chips above this pane, and the tab's
        // header would draw both a second time.
        BibleTypographyPanel(
            translation = t,
            moduleTitle = "",
            element = styleElement,
            onElementChange = {},
            style = t.elementStyle(styleElement, target),
            onStyleChange = { edited ->
                // Under All only the property that changed is written, so each translation keeps
                // everything else it had of its own.
                val shown = t.elementStyle(styleElement, target)
                updateEntry {
                    val next = if (all) it.elementStyle(styleElement, target).withChangesFrom(shown, edited) else edited
                    it.withElementStyle(styleElement, target, next)
                }
            },
            onTranslationChange = { transform -> updateEntry(transform) },
            onReset = {
                updateEntry { it.withElementStyle(styleElement, target, defaultElementStyle(styleElement, target)) }
            },
            availableFonts = fonts,
            // Auto-fit measures the verse that is live, which this dialog does not have in hand.
            autoFit = null,
            autoFitEnabled = false,
            showHeader = false,
            // Where the verse block sits on the slide, drawn beside the horizontal alignment. The
            // panel edits one element of one translation, while this places the whole block and is
            // one value for the Bible -- which is why it is written with `updateBible` rather than
            // `updateEntry`. The reference has [PositionControl] instead, since all it chooses is
            // which side of the verse it sits on.
            blockAlignment = if (styleElement == BibleStyleElement.TEXT) {
                {
                    BlockVerticalAlignmentControl(
                        selected = bs.verticalAlignment,
                        onSelect = { v -> updateBible { it.copy(verticalAlignment = v) } },
                    )
                }
            } else {
                null
            },
        )
        if (styleElement == BibleStyleElement.TEXT) {
            // How parallel translations are arranged against each other -- the same kind of control
            // as the vertical alignment: one value for the whole Bible rather than for this translation,
            // so it is written with `updateBible` and drawn on the verse text, which is the block it
            // arranges. The two shapes keep separate values: a full screen stacks by default and a
            // band splits by default, which is what they have always drawn, so one shared field
            // could not have preserved both.
            //
            // Two translations have to reach this output before the arrangement means anything.
            // `bibleTranslations` empty means "all of them", which is the usual case.
            val parallel = profile.bibleMode != Constants.SONG_LANG_OFF &&
                (profile.bibleTranslations.size > 1 || profile.bibleTranslations.isEmpty()) &&
                stack.size > 1
            if (parallel) {
                BilingualLayoutRow(
                    selected = if (lowerThird) bs.bilingualLayoutLowerThird else bs.bilingualLayout,
                    onSelect = { v ->
                        updateBible {
                            if (lowerThird) it.copy(bilingualLayoutLowerThird = v)
                            else it.copy(bilingualLayout = v)
                        }
                    },
                )
            }
        }
        // Whether the reference names its translation by abbreviation. One flag, not a pair: the
        // reference names a translation, and a translation is abbreviated the same way whichever
        // shape of output is drawing it. Absent on the verse text, which has no label to abbreviate.
        if (styleElement == BibleStyleElement.REFERENCE) {
            ToggleControl(
                label = stringResource(Res.string.customize_show_abbreviation),
                checked = t.showAbbreviation,
                onCheckedChange = { v -> updateEntry { it.copy(showAbbreviation = v) } },
            )
        }
    }
}
