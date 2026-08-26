package org.churchpresenter.settings

/**
 * Resolving one output's own appearance against the global settings document.
 *
 * An override on [ScreenAssignment] stores a whole [BibleSettings]/[SongSettings] rather than a
 * diff, so that the existing settings tabs can edit one without knowing they are editing an
 * override. Only its *appearance* is ever read back: the library folder, the file list, the
 * translation stack and the browsing panels stay one per install, because an output holding its own
 * copy of those could point at a folder the operator has since moved, or present a translation the
 * Bible tab no longer lists.
 */

/**
 * [override]'s appearance on top of this document's library, selection and panel state.
 *
 * The global stack decides *which* translations present and in what order; [override] supplies each
 * one's styling, matched by file name. A translation added to the stack after the override was made
 * has no styling there, and keeps the global styling rather than vanishing from the output.
 *
 * A translation's rename travels with the global entry rather than the override: what a translation
 * is *called* is one fact per install, like its file name, and the per-output settings surface does
 * not offer the field.
 */
fun BibleSettings.withAppearanceOf(override: BibleSettings): BibleSettings {
    val overrideStyles = override.translationList().associateBy { it.fileName }
    return override.copy(
        storageDirectory = storageDirectory,
        bibleFiles = bibleFiles,
        primaryBible = primaryBible,
        secondaryBible = secondaryBible,
        bibleColWidthBook = bibleColWidthBook,
        bibleColWidthChapter = bibleColWidthChapter,
        captionLanguage = captionLanguage,
        splitBrowseMode = splitBrowseMode,
        splitLivePanelWidth = splitLivePanelWidth,
        crossReferencesEnabled = crossReferencesEnabled,
        crossReferencesPanel = crossReferencesPanel,
        translations = translationList().map { global ->
            val styled = overrideStyles[global.fileName] ?: return@map global
            styled.copy(
                customName = global.customName,
                customAbbreviation = global.customAbbreviation,
            )
        },
    )
}

/** [override]'s appearance on top of this document's library and song-list column state. */
fun SongSettings.withAppearanceOf(override: SongSettings): SongSettings = override.copy(
    storageDirectory = storageDirectory,
    songFiles = songFiles,
    colWidthNumber = colWidthNumber,
    colWidthTitle = colWidthTitle,
    colWidthSongbook = colWidthSongbook,
    colWidthTune = colWidthTune,
    colWidthPlayCount = colWidthPlayCount,
    colWidthAuthor = colWidthAuthor,
    colWidthComposer = colWidthComposer,
    lyricsPanelWidthDp = lyricsPanelWidthDp,
    editorShowChords = editorShowChords,
)

/**
 * [override]'s lower-third window padding on top of this document's Lottie library.
 *
 * The four insets are the only appearance in [StreamingSettings]; everything else names a folder,
 * a preset or a saved search, and stays one per install.
 */
fun StreamingSettings.withAppearanceOf(override: StreamingSettings): StreamingSettings = copy(
    windowTop = override.windowTop,
    windowLeft = override.windowLeft,
    windowRight = override.windowRight,
    windowBottom = override.windowBottom,
)

/**
 * The settings [assignment]'s output should actually render with.
 *
 * Only the rendering paths see these; editing and persistence keep using the global document, so a
 * customized output never saves its own styling over everyone else's. The same shape, and the same
 * reason, as `withMirroredBackgrounds` in the app's `MainLogic`.
 *
 * An output with no override at all gets **this very instance** back rather than an equal copy —
 * that path is the overwhelmingly common one, and the presenter windows key `remember` and
 * `Crossfade` off these objects.
 */
fun AppSettings.resolvedFor(assignment: ScreenAssignment): AppSettings {
    if (!assignment.isCustomized) return this
    return copy(
        stageMonitorSettings = assignment.stageMonitorOverride ?: stageMonitorSettings,
        bibleSettings = assignment.bibleOverride
            ?.let { bibleSettings.withAppearanceOf(it) } ?: bibleSettings,
        songSettings = assignment.songOverride
            ?.let { songSettings.withAppearanceOf(it) } ?: songSettings,
        streamingSettings = assignment.streamingOverride
            ?.let { streamingSettings.withAppearanceOf(it) } ?: streamingSettings,
        dictionarySettings = assignment.dictionaryOverride ?: dictionarySettings,
        backgroundSettings = assignment.backgroundOverride ?: backgroundSettings,
    )
}
