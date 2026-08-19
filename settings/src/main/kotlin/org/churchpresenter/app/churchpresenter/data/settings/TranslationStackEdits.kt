package org.churchpresenter.app.churchpresenter.data.settings

import org.churchpresenter.app.churchpresenter.utils.Constants

/**
 * Editing the translation stack, with every output's selection carried along.
 *
 * An output names the translations it shows by **position** in the stack
 * ([ScreenAssignment.bibleTranslations]), so removing or reordering a translation moves the ground
 * under every one of those selections. Editing only [BibleSettings] leaves them pointing at whatever
 * has since slid into that position: delete the first of `[KJV, RST, NIV]` and the screen pinned to
 * position 1 goes from Russian to NIV, silently, in the middle of a service.
 *
 * These are the only correct way to remove or reorder — they update the stack and rewrite the
 * selections in the same step. [BibleSettings.removeTranslation] and
 * [BibleSettings.moveTranslation] remain for the stack alone; call them directly only where no
 * output selection can exist.
 *
 * Position, rather than a stable file name, is what the settings store; changing that is a migration
 * this does not attempt. Keeping the positions honest through an edit is the cheaper half of the
 * problem and covers what an operator actually does.
 */

/** Removes the translation at [index], and drops it from every output that named it. */
fun AppSettings.removeBibleTranslation(index: Int): AppSettings {
    val stack = bibleSettings.translationList()
    if (index !in stack.indices) return this
    return copy(bibleSettings = bibleSettings.removeTranslation(index))
        .remapOutputTranslations { position ->
            when {
                position == index -> null
                position > index -> position - 1
                else -> position
            }
        }
}

/** Moves the translation at [index] by [offset], and follows it in every output that named it. */
fun AppSettings.moveBibleTranslation(index: Int, offset: Int): AppSettings {
    val stack = bibleSettings.translationList()
    val target = index + offset
    if (index !in stack.indices || target !in stack.indices) return this
    return copy(bibleSettings = bibleSettings.moveTranslation(index, offset))
        .remapOutputTranslations { position ->
            when {
                position == index -> target
                // Everything the moved translation passed over shifts one place the other way.
                index < target && position in (index + 1)..target -> position - 1
                index > target && position in target until index -> position + 1
                else -> position
            }
        }
}

/** The Bible tab's swap button: the first two translations exchange places, selections included. */
fun AppSettings.swapBibleTranslations(): AppSettings = moveBibleTranslation(0, 1)

/**
 * First run: points the app at [directory] and puts the bundled [fileName] in the stack.
 *
 * Separate from its one caller in `main()` so it can be tested, and going through
 * [BibleSettings.addTranslation] rather than setting `primaryBible`: written straight to the legacy
 * field, the very first settings file the app ever saves is one whose stack is empty and whose
 * legacy pair is not — the drift `SettingsManager.repaired` now has to undo on every subsequent load.
 */
fun AppSettings.withBundledBible(directory: String, fileName: String): AppSettings =
    copy(bibleSettings = bibleSettings.copy(storageDirectory = directory).addTranslation(fileName))

/**
 * A bible just installed from the catalogue, presented if nothing else is.
 *
 * Deliberately not an unconditional add. The rule it replaces was "become the primary bible if there
 * isn't one", which — once the stack existed — could no longer be honoured: the legacy field it
 * tested is mirrored from the stack, so it was never empty and a downloaded module simply never
 * appeared anywhere. Restoring the intent means asking the stack instead. An unconditional add would
 * be a different rule altogether: browse the catalogue for an afternoon and every module you tried
 * is stacked on the output.
 */
fun AppSettings.withInstalledBible(fileName: String): AppSettings =
    if (bibleSettings.translationList().isEmpty()) {
        copy(bibleSettings = bibleSettings.addTranslation(fileName))
    } else {
        this
    }

/**
 * Rewrites every output's stored positions through [newPositionOf]; null means that translation is
 * gone. Covers browser sources as well as screens — the same shape, driven by the same UI.
 */
private fun AppSettings.remapOutputTranslations(newPositionOf: (Int) -> Int?): AppSettings =
    copy(
        projectionSettings = projectionSettings.copy(
            screenAssignments = projectionSettings.screenAssignments.map { it.remapped(newPositionOf) },
            browserSourceOutputs = projectionSettings.browserSourceOutputs.map { it.remapped(newPositionOf) },
        ),
    )

private fun ScreenAssignment.remapped(newPositionOf: (Int) -> Int?): ScreenAssignment {
    // An empty selection means "all of them", which stays true whatever the stack does.
    if (bibleTranslations.isEmpty()) return this
    val remapped = bibleTranslations.mapNotNull(newPositionOf).distinct().sorted()
    if (remapped == bibleTranslations) return this
    return if (remapped.isEmpty()) {
        // Every translation this output named has gone. Letting the selection fall empty would read
        // as "all of them" and put three languages on a screen deliberately narrowed to one, so its
        // scripture switches off instead: nothing shown rather than the wrong thing shown, and one
        // click to put back.
        copy(bibleMode = Constants.SONG_LANG_OFF, bibleTranslations = emptyList())
    } else {
        copy(bibleTranslations = remapped)
    }
}
