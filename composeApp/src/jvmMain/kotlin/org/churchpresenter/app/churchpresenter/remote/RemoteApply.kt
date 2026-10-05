package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.server.LiveStateDto
import java.io.File
import org.churchpresenter.bible.Bible
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.settings.BibleSyncMode
import org.churchpresenter.settings.LinkLayers
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.server.InstanceLinkLogSide
import org.churchpresenter.server.InstanceLinkLogger
import org.churchpresenter.server.InstanceLinkViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

/**
 * Applying what a *remote* instance sends to this one: the Instance Link follower path, and the
 * approved remote requests that arrive from a phone or a linked controller.
 *
 * This is behaviour, not window construction, and it used to live in `main.kt` where nothing could
 * reach it -- excluded from the coverage gate as app-entry wiring *and* sitting at 0%, so a defect
 * here was invisible twice over. What a follower does with the primary's live state decides what an
 * overflow room shows mid-service; it is worth testing on its own terms.
 *
 * Moved verbatim from `main.kt`; only visibility changed (private -> internal) so tests can reach it.
 * This file is the follower applying the primary's live state; whether it mirrors at all is
 * `RemoteMirroring.kt`, a remote request's schedule rows are `RemoteScheduleItems.kt`, and
 * projecting one straight onto the presenter is `RemoteProjection.kt`.
 */

/** Where fetched picture bytes are cached so PresenterManager.setSelectedImagePath (which needs a
 *  local path, not bytes) can display them like any other local file. */
internal val instanceLinkPictureCacheDir: File by lazy {
    File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/pictures").apply { mkdirs() }
}

/**
 * Applies a [LiveStateDto] received from another instance's CompanionServer to this instance's own
 * [PresenterManager], so an InstanceLink follower mirrors the primary's output. Bible verses, song
 * sections, announcements, website content, pictures, lower thirds (fetched by preset name),
 * media (streamed from the primary, no position sync — the DTO carries no transport state),
 * canvas scenes (matched by id against this instance's own local scenes), Q&A questions, and
 * Strong's dictionary entries (carried whole in the DTO) are all mirrored; presentations use
 * their own richer dedicated broadcast instead (see the remotePresentationSlide collector in
 * the caller). STT stays mode-only — there is no caption feed to mirror.
 */
internal suspend fun applyRemoteLiveState(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
    bibleSyncMode: BibleSyncMode = BibleSyncMode.FULL_REPLICA,
    localPrimaryBible: Bible? = null,
    /** This instance's own saved scenes — CANVAS mirroring is id-match only (no content endpoint). */
    localScenes: List<Scene> = emptyList(),
    /** Loads + starts media playback locally (MediaViewModel stays owned by its composable). */
    onPlayRemoteMedia: ((url: String, type: String) -> Unit)? = null,
    /** The layers this follower mirrors, from `LinkLayers`; changes to the others are left alone. */
    followedLayers: List<String> = LinkLayers.ALL,
) {
    val mode = runCatching { Presenting.valueOf(state.contentType) }.getOrNull()
    if (mode == null) {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to state.contentType, "resolved" to false, "reason" to "unknown_content_type")
        )
        return
    }
    val follows = { m: Presenting -> linkLayerOf(m) in followedLayers }
    val applied = !follows(mode) || applyRemoteContent(
        state, mode, presenterManager, instanceLinkViewModel, bibleSyncMode, localPrimaryBible, localScenes,
        onPlayRemoteMedia,
    )
    if (!applied) {
        return
    }
    followAir(state, mode, presenterManager, follows)
    followMessage(state, presenterManager, follows)
    presenterManager.setShowPresenterWindow(true)
}

/** The content half of [applyRemoteLiveState]: [mode]'s content, put in place; false when it cannot go live. */
@Suppress("LongParameterList")
private suspend fun applyRemoteContent(
    state: LiveStateDto,
    mode: Presenting,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
    bibleSyncMode: BibleSyncMode,
    localPrimaryBible: Bible?,
    localScenes: List<Scene>,
    onPlayRemoteMedia: ((url: String, type: String) -> Unit)?,
): Boolean {
    when (mode) {
        Presenting.BIBLE ->
            applyRemoteBible(state, presenterManager, bibleSyncMode, localPrimaryBible)
        Presenting.LYRICS -> if (state.songTitle != null) {
            // Positions first: setLyricSection reports the change, and reads them.
            presenterManager.setSongDisplaySectionIndex(state.songSectionIndex ?: -1)
            presenterManager.setSongDisplayLineIndex(state.songLineIndex ?: -1)
            presenterManager.setLyricSection(
                LyricSection(
                    title = state.songTitle.orEmpty(),
                    songNumber = state.songNumber ?: 0,
                    type = state.sectionType ?: "",
                    lines = state.lines ?: emptyList()
                )
            )
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER,
                "apply_live_state",
                mapOf("contentType" to "LYRICS", "resolved" to true)
            )
        } else {
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "LYRICS", "resolved" to false, "reason" to "no_song_title_in_state")
            )
        }
        Presenting.ANNOUNCEMENTS -> {
            val text = state.announcementText
            if (text != null) {
                presenterManager.setAnnouncementText(text)
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER,
                    "apply_live_state",
                    mapOf("contentType" to "ANNOUNCEMENTS", "resolved" to true)
                )
            } else {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "ANNOUNCEMENTS", "resolved" to false, "reason" to "no_text_in_state")
                )
            }
        }
        Presenting.WEBSITE -> {
            state.websiteUrl?.let { presenterManager.setWebsiteUrl(it) }
            state.websiteTitle?.let { presenterManager.setWebPageTitle(it) }
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "WEBSITE", "resolved" to (state.websiteUrl != null))
            )
        }
        Presenting.PICTURES ->
            if (!applyRemotePictures(state, presenterManager, instanceLinkViewModel)) return false
        Presenting.LOWER_THIRD -> applyRemoteLowerThird(state, presenterManager, instanceLinkViewModel)
        Presenting.MEDIA -> applyRemoteMedia(state, presenterManager, instanceLinkViewModel, onPlayRemoteMedia)
        Presenting.CANVAS -> applyRemoteCanvas(state, presenterManager, localScenes)
        Presenting.QA -> applyRemoteQa(state, presenterManager)
        Presenting.DICTIONARY -> applyRemoteDictionary(state, presenterManager)
        // Carried whole in the state itself -- see followMessage.
        Presenting.MESSAGE -> Unit
        else -> {
            // presentation: mirrored via its own dedicated broadcast (remotePresentationSlide
            // collector); stt: no caption feed exists to mirror. Mode still switches below.
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to mode.name, "resolved" to false, "reason" to "mode_only_no_feed")
            )
        }
    }
    return true
}

/** The BIBLE half of [applyRemoteLiveState]: either this instance's own wording, or the primary's. */
private fun applyRemoteBible(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    bibleSyncMode: BibleSyncMode,
    localPrimaryBible: Bible?,
) {
    val codeBook = state.verseCodeBook
    val codeChapter = state.verseCodeChapter
    val codeVerse = state.verseCodeVerse
    val hasFullCode = codeBook != null && codeChapter != null && codeVerse != null
    if (bibleSyncMode == BibleSyncMode.REFERENCE_ONLY && hasFullCode) {
        // Reference-only: never touch a downloaded file — resolve the SAME canonical verse in
        // this instance's own independently-configured (possibly different-language) Bible via
        // Bible.getVerseDetailsByCode, so the follower shows its own translation's wording, not
        // the primary's. If this Bible has no verse at that code (versification mismatch, or no
        // local bible configured), there's nothing sensible to show — quietly no-op.
        val result = localPrimaryBible?.getVerseDetailsByCode(codeBook, codeChapter, codeVerse)
        if (result != null) {
            presenterManager.setSelectedVerses(
                listOf(
                    SelectedVerse(
                        bibleAbbreviation = localPrimaryBible.getBibleAbbreviation(),
                        bibleName = localPrimaryBible.getBibleTitle(),
                        bookName = result.bookName,
                        chapter = result.displayChapter,
                        verseNumber = result.displayVerse,
                        verseText = result.verseText,
                        verseRange = state.verseRange ?: ""
                    )
                )
            )
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "BIBLE", "resolved" to true, "mode" to "reference_only")
            )
        } else {
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf(
                    "contentType" to "BIBLE", "resolved" to false, "mode" to "reference_only",
                    "reason" to if (localPrimaryBible == null) "no_local_bible_loaded" else "verse_code_not_found"
                )
            )
        }
    } else if (state.bookName != null) {
        // Full replica: the primary's own wording, verbatim.
        // setSelectedVerses (plural), not setSelectedVerse — only the plural setter feeds the
        // selectedVerses -> displayedVerses bridging LaunchedEffect that BiblePresenter actually
        // renders from; the singular setter alone leaves the screen blank despite the mode
        // correctly switching to BIBLE.
        presenterManager.setSelectedVerses(
            listOf(
                SelectedVerse(
                    bookName = state.bookName.orEmpty(),
                    chapter = state.chapter ?: 0,
                    verseNumber = state.verseNumber ?: 0,
                    verseText = state.verseText ?: "",
                    verseRange = state.verseRange ?: ""
                )
            )
        )
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "BIBLE", "resolved" to true, "mode" to "full_replica")
        )
    } else {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "BIBLE", "resolved" to false, "reason" to "no_book_name_in_state")
        )
    }
}

private suspend fun applyRemotePictures(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
): Boolean {
    val folderId = state.pictureFolderId
    val index = state.pictureIndex
    if (folderId != null && index != null) {
        val cacheFile = File(instanceLinkPictureCacheDir, "${folderId}_$index.jpg")
        if (!cacheFile.exists() && !cachePictureImage(cacheFile, folderId, index, instanceLinkViewModel)) {
            return false
        }
        presenterManager.setSelectedImagePath(cacheFile.absolutePath)
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER,
            "apply_live_state",
            mapOf("contentType" to "PICTURES", "resolved" to true)
        )
    } else {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "missing_folder_id_or_index")
        )
    }
    return true
}

private suspend fun applyRemoteMedia(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
    onPlayRemoteMedia: ((url: String, type: String) -> Unit)?,
) {
    // No position/transport sync in this pass: LiveStateDto carries which media is live,
    // not where playback is — a follower starts the same media from the top.
    val mediaType = state.mediaType
    val mediaUrl = state.mediaUrl
    val streamUrl = state.mediaId?.let { instanceLinkViewModel.mediaStreamUrl(it) }
    when {
        mediaType == Constants.MEDIA_TYPE_URL && mediaUrl != null && onPlayRemoteMedia != null -> {
            onPlayRemoteMedia(mediaUrl, mediaType)
            presenterManager.setCurrentMedia(mediaUrl, mediaType)
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "MEDIA", "resolved" to true, "source" to "url", "positionSync" to false)
            )
        }
        streamUrl != null && onPlayRemoteMedia != null -> {
            val type = mediaType ?: Constants.MEDIA_TYPE_LOCAL
            onPlayRemoteMedia(streamUrl, type)
            presenterManager.setCurrentMedia(streamUrl, type)
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "MEDIA", "resolved" to true, "source" to "stream", "positionSync" to false)
            )
        }
        else -> InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            // Media launched outside the primary's schedule has no stream mapping
            // (LiveStateDto.mediaId comes from its schedule-item → path map).
            mapOf("contentType" to "MEDIA", "resolved" to false, "reason" to "no_media_id")
        )
    }
}

private suspend fun applyRemoteLowerThird(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
) {
    val name = state.lowerThirdName
    if (name != null) {
        val bytes = instanceLinkViewModel.fetchLowerThirdJson(name)
        if (bytes != null) {
            presenterManager.setLottieContent(
                String(bytes, Charsets.UTF_8), pauseAtFrame = false, pauseFrame = -1f,
                pauseDurationMs = 2000L, presetName = name
            )
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER,
                "apply_live_state",
                mapOf("contentType" to "LOWER_THIRD", "resolved" to true)
            )
        } else {
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "LOWER_THIRD", "resolved" to false, "reason" to "fetch_failed")
            )
        }
    } else {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "LOWER_THIRD", "resolved" to false, "reason" to "no_name_in_state")
        )
    }
}


private fun applyRemoteCanvas(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    localScenes: List<Scene>,
) {
    val scene = state.sceneId?.let { id -> localScenes.find { it.id == id } }
    if (scene != null) {
        presenterManager.setActiveScene(scene)
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "CANVAS", "resolved" to true)
        )
    } else {
        // Id-match only: scene content isn't fetchable over the link — mirroring works
        // when the same scenes.json exists on both instances. Mode still switches.
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf(
                "contentType" to "CANVAS", "resolved" to false,
                "reason" to "scene_not_found_locally", "sceneName" to state.sceneName
            )
        )
    }
}

private fun applyRemoteQa(
    state: LiveStateDto,
    presenterManager: PresenterManager,
) {
    val questionId = state.questionId
    val questionText = state.questionText
    if (questionId != null && questionText != null) {
        presenterManager.setDisplayedQuestion(
            Question(
                id = questionId,
                text = questionText,
                timestamp = System.currentTimeMillis(),
                status = QuestionStatus.APPROVED
            )
        )
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER,
            "apply_live_state",
            mapOf("contentType" to "QA", "resolved" to true)
        )
    } else {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "QA", "resolved" to false, "reason" to "no_question_in_state")
        )
    }
}

private fun applyRemoteDictionary(
    state: LiveStateDto,
    presenterManager: PresenterManager,
) {
    val entry = state.dictionaryEntry
    val word = state.dictionaryWord
    when {
        entry != null -> {
            presenterManager.setDisplayedDictionaryEntry(entry)
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER,
                "apply_live_state",
                mapOf("contentType" to "DICTIONARY", "resolved" to true)
            )
        }
        word != null -> {
            // Old primary that doesn't carry the full entry — show what we have.
            presenterManager.setDisplayedDictionaryEntry(
                StrongsEntry(number = "", word = word, transliteration = "", pronunciation = "", definition = "")
            )
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "DICTIONARY", "resolved" to true, "partial" to true)
            )
        }
        else -> InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "DICTIONARY", "resolved" to false, "reason" to "no_word_in_state")
        )
    }
}

/** Fetches one picture into the follower's cache; false once the failure has been logged. */
private suspend fun cachePictureImage(
    cacheFile: File,
    folderId: String,
    index: Int,
    instanceLinkViewModel: InstanceLinkViewModel,
): Boolean {
    val bytes = instanceLinkViewModel.fetchPictureImageBytes(folderId, index)
    if (bytes == null) {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "fetch_failed")
        )
        return false
    }
    // Temp-file + rename: this apply can be cancelled mid-write by a newer live state
    // (collectLatest) — a truncated file must never land under the final name, or the exists()
    // cache gate would trust it forever.
    val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(cacheFile)) tmp.delete()
    if (cacheFile.exists()) return true
    InstanceLinkLogger.log(
        InstanceLinkLogSide.FOLLOWER, "apply_live_state",
        mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "cache_write_failed")
    )
    return false
}
