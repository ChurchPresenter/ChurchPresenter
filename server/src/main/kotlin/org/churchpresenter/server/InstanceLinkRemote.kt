package org.churchpresenter.server

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.BackgroundSettings

/**
 * What an instance linked to a primary can ask of it: changes to its schedule (still gated by its
 * own operator's approval) and, in Controller mode, what to put on screen. See [InstanceLinkClient]
 * for each command's own notes.
 */
interface InstanceLinkCommands : InstanceLinkScheduleCommands, InstanceLinkNavigation {
    fun sendProject(item: ScheduleItem)
    fun sendSelectBibleVerse(bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String)
    fun sendSelectPicture(folderId: String, index: Int, fileName: String?)
    fun sendSelectSongSection(number: String, section: Int, lineIndex: Int = -1)
    fun sendSelectSlide(id: String, index: Int)
    fun sendClear()
    fun sendBibleHold(hold: Boolean)
}

/** Changes to the primary's schedule, each still waiting on its own operator's approval. */
interface InstanceLinkScheduleCommands {
    fun sendAddToSchedule(item: ScheduleItem)
    fun sendRemoveFromSchedule(id: String)
}

/** Stepping the primary's pictures and slides. */
interface InstanceLinkNavigation {
    fun sendNextPicture()
    fun sendPreviousPicture()
    fun sendNextSlide()
    fun sendPreviousSlide()
}

/** What a follower fetches from the primary on demand. See [InstanceLinkClient] for each. */
interface InstanceLinkFetches {
    fun mediaStreamUrl(mediaId: String): String?
    suspend fun fetchSongDetail(number: String, songbook: String): SongDetailDto?
    suspend fun fetchPictureImageBytes(folderId: String, index: Int): ByteArray?
    suspend fun fetchPresentationSlideBytes(id: String, index: Int): ByteArray?
    suspend fun fetchBibleFile(): ByteArray?
    suspend fun fetchSecondaryBibleFile(): ByteArray?
    suspend fun fetchBibleTranslations(): List<Pair<String, ByteArray>>
    suspend fun fetchLowerThirdJson(name: String): ByteArray?
    suspend fun fetchBackgroundSettings(): BackgroundSettings?
    suspend fun fetchBackgroundAsset(slot: String, isVideo: Boolean): ByteArray?
}
