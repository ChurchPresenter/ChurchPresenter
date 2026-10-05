package org.churchpresenter.settings

/**
 * The parts a linked profile can follow a master by, each its own: one per page of the Profiles
 * editor that holds settings, named by the [prefixes] of the values it holds.
 *
 * A linked profile follows its main master ([OutputProfile.parentId]) in every section, except where
 * [OutputProfile.sectionMasters] names another master or [OWN_SECTION] -- a Sign language screen
 * following Livestream for its Bible and Sanctuary for its songs. The display mode and the profile's
 * identity are no section's: they always come from the main master, or stay the profile's own.
 *
 * [id] is what [OutputProfile.sectionMasters] is keyed by in the settings file, so it never changes.
 */
enum class ProfileSection(val id: String, val prefixes: List<String>) {
    CONTENT("content", CONTENT_PATHS),
    BIBLE("bible", listOf("bibleSettings")),
    SONGS("songs", listOf("songSettings")),
    // The background fields the Bible and Songs pages edit are still the background's, as their
    // paths say: a band's look is one thing wherever it is edited from.
    BACKGROUND("background", listOf("backgroundSettings", "backgroundOverrides")),
    CAPTIONS("captions", listOf("sttSettings")),
    SUBTITLES("subtitles", listOf("mediaSettings")),
    QA("qa", listOf("qaSettings")),
    DICTIONARY("dictionary", listOf("dictionarySettings")),
    STAGE("stage", listOf("stageMonitorSettings")),
    ;

    /** Whether [path] is one of this section's values. */
    fun holds(path: String): Boolean = prefixes.any { pathWithin(path, it) }

    companion object {
        /** The section with [id], or null for one this version does not know. */
        fun byId(id: String): ProfileSection? = entries.firstOrNull { it.id == id }

        /** The section [path] belongs to, or null for a value no section holds -- the display mode. */
        fun of(path: String): ProfileSection? = entries.firstOrNull { it.holds(path) }
    }
}

/** The [OutputProfile.sectionMasters] value of a section that follows no master: its values are its own. */
const val OWN_SECTION = ""

/** Everything the Content & sources page decides: what is shown, from where, and how it fits. */
val CONTENT_PATHS = listOf(
    "bibleMode", "bibleTranslations", "songMode", "songTranslations", "songLookAhead", "showChords",
    "showTransposeControls", "look", "pictureScaleMode",
    "mediaScaleMode", "lowerThirdPlacements",
    "lowerThirdOverContent", "announcementsOverContent", "captionsOverContent",
)
