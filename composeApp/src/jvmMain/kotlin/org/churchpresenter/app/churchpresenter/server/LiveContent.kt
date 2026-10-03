package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.dictionary.data.StrongsEntry

/**
 * Whatever is live right now, as [CompanionServer.updateLiveState] announces it. [mode] is the
 * live mode's name; every other field is null when it is not what is on screen.
 */
data class LiveContent(
    val mode: String,
    val bibleVerse: SelectedVerse? = null,
    val lyricSection: LyricSection? = null,
    val pictureImagePath: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val announcementText: String? = null,
    val websiteUrl: String? = null,
    val websiteTitle: String? = null,
    val sceneId: String? = null,
    val sceneName: String? = null,
    val questionId: String? = null,
    val questionText: String? = null,
    val dictionaryWord: String? = null,
    val dictionaryEntry: StrongsEntry? = null,
    val lowerThirdName: String? = null,
    /**
     * Canonical verse code (book, chapter, verse) computed from this instance's OWN loaded bible --
     * see LiveStateDto.verseCodeBook and BibleSyncMode.REFERENCE_ONLY. Null when not applicable.
     */
    val verseCode: Triple<Int, Int, Int>? = null,
    /** Current line/section position within [lyricSection] -- see LiveStateDto.songSectionIndex. */
    val songSectionIndex: Int? = null,
    val songLineIndex: Int? = null,
)
