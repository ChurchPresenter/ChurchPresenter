package org.churchpresenter.settings

import kotlinx.serialization.json.JsonObject

/**
 * What an [OutputProfile] may not say about Bible/Song content -- see
 * [AppSettings.resolvedFor] (`OutputProfileResolution.kt`), which is where these are used.
 *
 * The library folders, the file lists and the browsing panels are one per install, because a
 * profile holding its own copy could point at a folder the operator has since moved. Those keys are
 * dropped from a profile's styling as it is resolved.
 */

/** The song settings a profile never carries: one per install, whatever any one output shows. */
val SONG_GLOBAL_KEYS = setOf(
    "storageDirectory", "songFiles", "colWidthNumber", "colWidthTitle", "colWidthSongbook",
    "colWidthTune", "colWidthPlayCount", "colWidthAuthor", "colWidthComposer",
    "lyricsPanelWidthDp", "editorShowChords", "languageNames", "languageOrder",
)

/**
 * The caption settings a profile never carries: which server to listen to. Everything about what
 * captions show and how they look on an output -- down to how many segments it keeps -- is the
 * profile's.
 */
val STT_GLOBAL_KEYS = setOf("serverUrl", "lastConnectedUrl")

/**
 * The Q&A settings a profile never carries: how the audience submits questions, and what the QR
 * code's link page says. How a question and its QR code look on screen is the profile's.
 */
val QA_GLOBAL_KEYS = setOf("rateLimitCooldownSeconds", "votingEnabled", "qrCodeMessage")

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
 * so a profile's Bible styling carries each translation whole and they are matched back on by name
 * when the output is resolved. Which translations present, and in what order, stays the document's.
 */
const val BIBLE_STACK_KEY = "translations"

/**
 * The Bible's keep-list projection ([styleTree]), with the translation *stack* still the document's.
 *
 * Which translations are presented, and in what order, is one decision for the whole install -- a
 * profile styles them, it does not choose them. The stack is a list matched by file name rather
 * than by position, so it cannot be merged key by key like everything else; [styleTree] carries
 * each styled translation whole and they are matched back on by name here.
 */
internal fun BibleSettings.withSparseBibleOverride(styleTree: JsonObject?): BibleSettings {
    if (styleTree == null || styleTree.isEmpty()) return this
    val merged = withSparseOverride(this, styleTree, BibleSettings.serializer())
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
