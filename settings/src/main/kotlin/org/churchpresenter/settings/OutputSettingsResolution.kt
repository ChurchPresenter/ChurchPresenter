package org.churchpresenter.settings

import kotlinx.serialization.json.JsonObject

/**
 * Resolving one output's own appearance against the global settings document.
 *
 * An override on [ScreenAssignment] is a **sparse tree of what that screen changed** -- see
 * [sparseOverrideOf]. A setting the operator never touched is absent from it, and absent means
 * follow the document, so a screen can only ever hold a value it was actually given. That is the
 * whole of the rule; there is no list here of what an override is allowed to say.
 *
 * What a screen may not say at all is named below: the library folders, the file lists and the
 * browsing panels are one per install, because an output holding its own copy could point at a
 * folder the operator has since moved. Those keys are dropped as the override is written.
 */

/** The song settings a screen never carries: one per install, whatever any one output shows. */
val SONG_GLOBAL_KEYS = setOf(
    "storageDirectory", "songFiles", "colWidthNumber", "colWidthTitle", "colWidthSongbook",
    "colWidthTune", "colWidthPlayCount", "colWidthAuthor", "colWidthComposer",
    "lyricsPanelWidthDp", "editorShowChords",
)

/** The Bible's equivalent. [BIBLE_STACK_KEY] is excluded separately -- it is styled, not chosen. */
val BIBLE_GLOBAL_KEYS = setOf(
    "storageDirectory", "bibleFiles", "primaryBible", "secondaryBible",
    "bibleColWidthBook", "bibleColWidthChapter", "captionLanguage",
    "splitBrowseMode", "splitLivePanelWidth", "crossReferencesEnabled", "crossReferencesPanel",
)

/**
 * The translation stack, which is matched by file name rather than by position.
 *
 * A list cannot be diffed entry by entry without giving position a meaning it does not have here,
 * so the override carries each styled translation whole and they are matched back on by name when
 * the output is resolved. Which translations present, and in what order, stays the document's.
 */
const val BIBLE_STACK_KEY = "translations"

fun AppSettings.resolvedFor(assignment: ScreenAssignment): AppSettings {
    if (!assignment.isCustomized) return this
    return copy(
        stageMonitorSettings = withSparseOverride(
            stageMonitorSettings, assignment.stageMonitorOverride, StageMonitorSettings.serializer(),
        ),
        bibleSettings = bibleSettings.withSparseBibleOverride(assignment.bibleOverride),
        songSettings = withSparseOverride(songSettings, assignment.songOverride, SongSettings.serializer()),
        dictionarySettings = withSparseOverride(
            dictionarySettings, assignment.dictionaryOverride, DictionarySettings.serializer(),
        ),
        backgroundSettings = withSparseOverride(
            backgroundSettings, assignment.backgroundOverride, BackgroundSettings.serializer(),
        ),
    )
}

/**
 * The Bible's sparse override, with the translation *stack* still the document's.
 *
 * Which translations are presented, and in what order, is one decision for the whole install --
 * a screen styles them, it does not choose them. The stack is a list matched by file name rather
 * than by position, so it cannot be diffed entry by entry like everything else; the override
 * carries each styled translation whole and they are matched back on by name here, exactly as the
 * snapshot model did.
 */
private fun BibleSettings.withSparseBibleOverride(override: JsonObject?): BibleSettings {
    if (override == null || override.isEmpty()) return this
    val merged = withSparseOverride(this, override, BibleSettings.serializer())
    val overrideStyles = merged.translationList().associateBy { it.fileName }
    return merged.copy(
        translations = translationList().map { global ->
            val styled = overrideStyles[global.fileName] ?: return@map global
            styled.copy(
                customName = global.customName,
                customAbbreviation = global.customAbbreviation,
            )
        },
    )
}

// ── Writing an override ─────────────────────────────────────────────────────────────────────────
//
// One per category, each naming what that category keeps global. The dialog edits a resolved copy
// of the settings and hands the edited one back here; what is stored is the difference, so a screen
// that changed one colour stores one colour.

fun songOverrideOf(global: SongSettings, customized: SongSettings): JsonObject? =
    sparseOverrideOf(global, customized, SongSettings.serializer(), ignoredKeys = SONG_GLOBAL_KEYS)

fun bibleOverrideOf(global: BibleSettings, customized: BibleSettings): JsonObject? =
    sparseOverrideOf(
        global,
        customized,
        BibleSettings.serializer(),
        ignoredKeys = BIBLE_GLOBAL_KEYS,
        atomicKeys = setOf(BIBLE_STACK_KEY),
    )

fun dictionaryOverrideOf(global: DictionarySettings, customized: DictionarySettings): JsonObject? =
    sparseOverrideOf(global, customized, DictionarySettings.serializer())

fun backgroundOverrideOf(global: BackgroundSettings, customized: BackgroundSettings): JsonObject? =
    sparseOverrideOf(global, customized, BackgroundSettings.serializer())

fun stageMonitorOverrideOf(global: StageMonitorSettings, customized: StageMonitorSettings): JsonObject? =
    sparseOverrideOf(global, customized, StageMonitorSettings.serializer())
