package org.churchpresenter.server

import io.ktor.serialization.kotlinx.json.json
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.churchpresenter.core.models.qa.toDto
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants

/*
 * The schedule, the pictures and backgrounds behind it, what is live, and the server's own access
 * settings -- each pushed in by the app and published to the clients.
 *
 * Functions of [CompanionServer], kept beside it rather than in it so no one file holds all of
 * its behaviour; they read and write the server's own state.
 */

/** Records the current background settings for GET /api/backgrounds — only consumed by a
 *  follower that opted in to mirroring backgrounds (see InstanceLinkSettings.mirrorBackgrounds). */
fun CompanionServer.updateBackgroundSettings(settings: BackgroundSettings) {
    if (_backgroundSettings.value == settings) return
    _backgroundSettings.value = settings
    InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, "state_updated", mapOf("type" to "background_settings"))
    // Invalidation signal for followers mirroring backgrounds — they clear their asset
    // cache and re-fetch on this event.
    broadcast(WebSocketMessage(type = Constants.WS_EVENT_BACKGROUNDS_UPDATED, payload = ""))
}

/**
 * Publishes a picture folder to connected companions and tells them it changed.
 * The catalogue itself is built by [PictureLibrary].
 */
fun CompanionServer.updatePictures(
    folderId: String,
    folderName: String,
    folderPath: String,
    imageFiles: List<File>
) {
    val catalog = pictures.update(folderId, folderName, folderPath, imageFiles)
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_PICTURES_UPDATED,
        payload = json.encodeToString(PictureFolderResponse.serializer(), catalog)
    ))
}

/**
 * The [File] for a specific image by folder ID and zero-based index, or null if not found.
 * Used by the remote-select handler in MainDesktop so the correct file is presented even when
 * the requested folder differs from the one open in the Pictures tab (e.g. a `device_uploads`
 * selection).
 */
fun CompanionServer.getImageFile(folderId: String, index: Int): File? = pictures.imageFile(folderId, index)

fun CompanionServer.updateSchedule(items: List<ScheduleItem>) {
    items.forEach(::registerScheduleItemResources)
    val dtos = items.map { it.toDto() }
    _schedule.value = dtos
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_SCHEDULE_UPDATED,
        payload = json.encodeToString(ScheduleResponse.serializer(), ScheduleResponse(dtos, dtos.size))
    ))
}

/**
 * Server-side resources a schedule item needs before clients can ask for it: picture folders
 * are catalogued, presentations start rendering in the background, and local media paths are
 * recorded so the media endpoint can serve them. Item types with nothing to register fall
 * through.
 *
 * Split out of [updateSchedule]'s mapping loop so [toDto] stays a pure function of the item.
 * Runs for every item before any is mapped; the DTOs don't read anything this writes, so the
 * published schedule is identical either way.
 */
private fun CompanionServer.registerScheduleItemResources(item: ScheduleItem) {
    when (item) {
        is ScheduleItem.PictureItem -> scope.launch(Dispatchers.IO) {
            pictures.registerScheduleFolder(item.id, item.folderPath, item.folderName)
        }
        is ScheduleItem.PresentationItem -> {
            val presentationId = item.filePath.hashCode().toUInt().toString(16)
            presentations._scheduleItemToPresentationId[item.id] = presentationId
            presentations._presentationFilePaths[presentationId] = item.filePath
            if (!presentations._slideBytes.containsKey(presentationId) &&
                presentations._renderingPresentations.putIfAbsent(presentationId, Unit) == null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        // One render at a time — see presentations.presentationRenderMutex.
                        presentations.presentationRenderMutex.withLock {
                            presentations.renderPresentationForServer(presentationId, item.filePath)
                        }
                    } finally {
                        presentations._renderingPresentations.remove(presentationId)
                    }
                }
            }
        }
        is ScheduleItem.MediaItem ->
            if (item.mediaType == "local") _scheduleItemToMediaPath[item.id] = item.mediaUrl
        else -> Unit
    }
}

/**
 * Broadcasts a snapshot of whatever is currently live — fills the gap for content types with
 * no dedicated "now live" event (bible, songs, pictures, media, lower thirds, announcements,
 * websites, scenes, Q&A, dictionary). Presentations rely on the existing slide-changed events
 * instead — [LiveContent.mode] == "PRESENTATION" here is informational only.
 */
fun CompanionServer.updateLiveState(content: LiveContent) = with(content) {
    val (pictureFolderId, pictureIndex) = pictures.locate(pictureImagePath)
    val mediaId = mediaUrl?.let { url -> _scheduleItemToMediaPath.entries.find { it.value == url }?.key }
    val dto = LiveStateDto(
        contentType = mode,
        bookName = bibleVerse?.bookName?.ifEmpty { null },
        chapter = bibleVerse?.chapter,
        verseNumber = bibleVerse?.verseNumber,
        verseRange = bibleVerse?.verseRange?.ifEmpty { null },
        verseText = bibleVerse?.verseText,
        verseCodeBook = verseCode?.first,
        verseCodeChapter = verseCode?.second,
        verseCodeVerse = verseCode?.third,
        songTitle = lyricSection?.title?.ifEmpty { null },
        songNumber = lyricSection?.songNumber,
        sectionType = lyricSection?.type?.ifEmpty { null },
        lines = lyricSection?.lines,
        songSectionIndex = songSectionIndex,
        songLineIndex = songLineIndex,
        pictureFolderId = pictureFolderId,
        pictureIndex = pictureIndex,
        mediaId = mediaId,
        mediaUrl = mediaUrl?.ifEmpty { null },
        mediaType = mediaType?.ifEmpty { null },
        announcementText = announcementText?.ifEmpty { null },
        websiteUrl = websiteUrl?.ifEmpty { null },
        websiteTitle = websiteTitle?.ifEmpty { null },
        sceneId = sceneId,
        sceneName = sceneName,
        questionId = questionId,
        questionText = questionText,
        dictionaryWord = dictionaryWord,
        dictionaryEntry = dictionaryEntry,
        lowerThirdName = lowerThirdName?.ifEmpty { null },
        liveSlide = liveSlide,
        overlays = overlays,
    )
    // Skip byte-identical re-broadcasts (content setters fire on every call, even when
    // nothing changed) — same early-return pattern the other update* functions use. Protects
    // the shared broadcast buffer from floods that could evict messages for slow clients.
    if (_liveState.value == dto) return@with
    _liveState.value = dto
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_LIVE_STATE_CHANGED,
        payload = json.encodeToString(LiveStateDto.serializer(), dto)
    ))
}

/** Update API key settings without restarting the server. */
fun CompanionServer.updateApiKey(enabled: Boolean, key: String) {
    _apiKeyEnabled.value = enabled
    _apiKey.value = key
}

/** Allow or disallow file uploads from mobile devices without restarting the server. */
fun CompanionServer.updateFileUploadEnabled(enabled: Boolean) {
    _fileUploadEnabled.value = enabled
    InstanceLinkLogger.log(
        InstanceLinkLogSide.PRIMARY,
        "state_updated",
        mapOf("type" to "file_upload_enabled", "enabled" to enabled)
    )
}

/** Update the max media-upload size (MB) without restarting the server. */
fun CompanionServer.updateMaxMediaUploadMb(mb: Int) {
    _maxMediaUploadMb.value = mb.coerceAtLeast(1)
}
