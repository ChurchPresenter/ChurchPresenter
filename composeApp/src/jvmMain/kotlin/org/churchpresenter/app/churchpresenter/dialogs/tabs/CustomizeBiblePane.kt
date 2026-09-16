package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputStyleScope

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
    /** Which entry of the ordered stack is being styled -- see [CustomizeTranslationChips]. */
    translationIndex: Int,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val scope = LocalOutputStyleScope.current
    val lowerThird = scope == OutputStyleScope.LOWER_THIRD
    val fonts = rememberSystemFonts()
    val bs = settings.bibleSettings
    val stack = bs.translationList()
    // A shelf with nothing configured still has a Bible style to edit, and an index left over from
    // a longer stack must not read off the end.
    val index = translationIndex.coerceIn(0, (stack.size - 1).coerceAtLeast(0))
    val t = stack.getOrNull(index) ?: BibleTranslationSettings()

    // Writes the selected entry alone, through the same `updateTranslation` the global tab uses,
    // so both edit the stack by one path.
    fun updateEntry(transform: (BibleTranslationSettings) -> BibleTranslationSettings) {
        onSettingsChange { s ->
            s.copy(bibleSettings = s.bibleSettings.updateTranslation(index, transform))
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
            onStyleChange = { edited -> updateEntry { it.withElementStyle(styleElement, target, edited) } },
            onTranslationChange = { transform -> updateEntry(transform) },
            onReset = {
                updateEntry { it.withElementStyle(styleElement, target, defaultElementStyle(styleElement, target)) }
            },
            availableFonts = fonts,
            // Auto-fit measures the verse that is live, which this dialog does not have in hand.
            autoFit = null,
            autoFitEnabled = false,
            showHeader = false,
        )
    }
}
