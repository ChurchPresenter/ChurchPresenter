package org.churchpresenter.profiles

import org.churchpresenter.presenter.styleElement
import org.churchpresenter.presenter.BibleStyleElement
import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_box
import org.churchpresenter.strings.generated.resources.profile_box_pick_translation
import org.churchpresenter.presenter.bibleBoxKey
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.withBox
import org.jetbrains.compose.resources.stringResource

/**
 * The box the Bible page's rows edit: its key, the box as stored, and where in the stack its
 * translation sits. [needsTranslation] when All is picked on a stack of several, each with a box of
 * its own, where there is no one box to edit.
 */
internal data class BibleBoxTarget(
    val key: String,
    val box: TextBox,
    val slot: Int,
    val slots: Int,
    val needsTranslation: Boolean = false,
)

/** The box the rows edit for this edit's element and translation. */
internal fun BibleEdit.boxTarget(): BibleBoxTarget? {
    val shared = bs.textBoxOptions.sharedLanguageBox
    val slot = when {
        !all -> index
        stack.size <= 1 || shared -> 0
        else -> return BibleBoxTarget("", TextBox(), 0, stack.size, needsTranslation = true)
    }
    val fileName = stack.getOrNull(slot)?.fileName ?: return null
    val key = bs.bibleBoxKey(styleElement, lowerThird, fileName)
    return BibleBoxTarget(key, bs.textBoxes.boxAt(key), slot, stack.size.coerceAtLeast(1))
}

/**
 * Where a translation's box starts the first time it is turned on, in percent of the whole screen:
 * the stack split into bands down the screen, the reference a strip across the top of its band.
 */
internal fun defaultBibleBox(element: BibleStyleElement, slot: Int, slots: Int): TextBox {
    val band = BIBLE_BOX_SPAN / slots
    val top = BIBLE_BOX_TOP + slot * band
    return if (element == BibleStyleElement.REFERENCE) {
        TextBox(xPercent = 5f, yPercent = top, widthPercent = 90f, heightPercent = band * REFERENCE_SHARE)
    } else {
        TextBox(
            xPercent = 5f,
            yPercent = top + band * REFERENCE_SHARE,
            widthPercent = 90f,
            heightPercent = band * (1f - REFERENCE_SHARE),
        )
    }
}

private const val BIBLE_BOX_TOP = 8f
private const val BIBLE_BOX_SPAN = 84f
private const val REFERENCE_SHARE = 0.2f

private val BIBLE_BOX_PATHS = listOf("bibleSettings.textBoxes")
private val BIBLE_BOX_OPTION_PATHS = listOf("bibleSettings.textBoxOptions")

/**
 * The Bible page's box rows for [target], or -- where All is picked on a stack whose translations
 * each have their own box -- a line saying to pick a translation first.
 */
@Composable
internal fun BibleBoxRows(target: BibleBoxTarget?, edit: BibleEdit) {
    if (target == null) return
    if (target.needsTranslation) {
        SettingsRow(
            stringResource(Res.string.profile_box),
            sub = stringResource(Res.string.profile_box_pick_translation),
        ) {}
        return
    }
    TextBoxRows(
        box = target.box,
        onBox = { box -> edit.updateBible { it.copy(textBoxes = it.textBoxes.withBox(target.key, box)) } },
        startBox = defaultBibleBox(edit.styleElement, target.slot, target.slots),
        options = edit.bs.textBoxOptions,
        onOptions = { options -> edit.updateBible { it.copy(textBoxOptions = options) } },
        perLanguage = edit.stack.size > 1,
        lowerThird = edit.lowerThird,
        boxPaths = BIBLE_BOX_PATHS,
        optionPaths = BIBLE_BOX_OPTION_PATHS,
    )
}
