package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.viewmodel.propsOnAir
import org.churchpresenter.app.churchpresenter.viewmodel.messageOnAir
import org.churchpresenter.server.updateLiveState
import org.churchpresenter.server.LiveContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import java.util.Locale
import org.churchpresenter.bible.Bible
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.sharedui.utils.LiveHistoryEntry
import org.churchpresenter.sharedui.utils.LiveHistoryLogger
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.hasAudienceOutput
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.isOverlay

/**
 * Broadcasts this instance's live content to any connected InstanceLink follower.
 *
 * [appSettings] and [primaryBible] are providers rather than values: the callback installed here
 * outlives the composition that installs it, and reading a captured value would freeze it at the
 * moment of installation.
 *
 * Lifted out of `main()`: ordinary effects with no window attached, so unlike the rest of main.kt
 * they can be composed — and tested — on their own.
 */
@Composable
internal fun LiveStateBroadcastWiring(
    appSettings: () -> AppSettings,
    primaryBible: () -> Bible?,
    presenterManager: PresenterManager,
    companionServer: CompanionServer,
    screenCountForUsage: Int,
    deckLinkCountForUsage: Int,
) {
    LaunchedEffect(Unit) {
        presenterManager.onLiveStateChanged = { pm, source ->
            // The one-off "this install has actually shown something to a congregation" mark.
            // Costs a file read per live change only until it fires, then never writes again.
            if (pm.anythingLive &&
                hasAudienceOutput(
                    appSettings().projectionSettings.screenAssignments,
                    screenCountForUsage,
                    deckLinkCountForUsage,
                )
            ) {
                UsageEvents.recordOncePerInstall(UsageEvent.FIRST_LIVE_ON_SCREEN)
            }
            val liveVerse = pm.selectedVerse.value
            // Resolved whatever this change was: the history logs the verse for as long as it is up.
            val liveCode = liveVerseCode(
                bookName = liveVerse.bookName,
                chapter = liveVerse.chapter,
                verseNumber = liveVerse.verseNumber,
                bookIdByName = { name -> primaryBible()?.getBookIdByName(name) },
                codeReference = { bookId, chapter, verse ->
                    primaryBible()?.getCodeReference(bookId, chapter, verse)
                },
            )
            val verseCode = liveCode.takeIf { source == Presenting.BIBLE }
            // Beside the broadcast and ahead of it, so it is written whether or not the server runs.
            LiveHistoryLogger.logLiveState(liveHistoryEntryOf(pm, liveCode, source))
            companionServer.updateLiveState(
                LiveContent(
                    mode = source.name,
                    bibleVerse = pm.selectedVerse.value,
                    lyricSection = pm.lyricSection.value,
                    pictureImagePath = pm.selectedImagePath.value,
                    mediaUrl = nullIfEmpty(pm.currentMediaUrl.value),
                    mediaType = nullIfEmpty(pm.currentMediaType.value),
                    announcementText = nullIfEmpty(pm.announcementText.value),
                    websiteUrl = nullIfEmpty(pm.websiteUrl.value),
                    websiteTitle = nullIfEmpty(pm.webPageTitle.value),
                    sceneId = pm.activeScene.value?.id,
                    sceneName = pm.activeScene.value?.name,
                    questionId = pm.displayedQuestion.value?.id,
                    questionText = pm.displayedQuestion.value?.text,
                    dictionaryWord = pm.displayedDictionaryEntry.value?.word,
                    dictionaryEntry = pm.displayedDictionaryEntry.value,
                    lowerThirdName = nullIfEmpty(pm.currentLowerThirdName.value),
                    verseCode = verseCode,
                    songSectionIndex = livePositionOrNull(source, Presenting.LYRICS, pm.songDisplaySectionIndex.value),
                    songLineIndex = livePositionOrNull(source, Presenting.LYRICS, pm.songDisplayLineIndex.value),
                    liveSlide = pm.slideContent.value.name,
                    overlays = pm.overlays.value.map { it.name },
                    message = pm.messageOnAir?.text,
                    messageDurationSeconds = pm.messageOnAir?.durationSeconds,
                    props = pm.propsOnAir.toList(),
                ),
            )
        }
    }
}

/**
 * The on-screen history line for what [pm] has live right now. It follows the live mode, not the
 * content type of the change that triggered it, so content pushed ahead of a mode switch (a song
 * staged while a verse is up) does not appear until it is actually on screen. Identifiers only.
 *
 * [verseCode] is the canonical code of [PresenterManager.selectedVerse] through the primary Bible,
 * null when it cannot be resolved; it is only written while BIBLE is the mode.
 *
 * An overlay is logged when [source] -- the change being logged -- is that overlay going up; any
 * other change logs the slide, so a song moving on under a lower third is still in the history.
 */
internal fun liveHistoryEntryOf(
    pm: PresenterManager,
    verseCode: Triple<Int, Int, Int>?,
    source: Presenting = pm.lastLive.value,
): LiveHistoryEntry {
    val mode = if (source.isOverlay && pm.isLive(source)) source else pm.slideContent.value
    val none = LiveHistoryEntry(Presenting.NONE.name)
    return when (mode) {
        Presenting.LYRICS -> {
            val section = pm.lyricSection.value
            if (section.title.isBlank() && section.lines.isEmpty()) none
            else LiveHistoryEntry(
                contentType = mode.name,
                songNumber = section.songNumber,
                songTitle = section.title,
                sectionIndex = pm.songDisplaySectionIndex.value,
                sectionType = section.type.ifEmpty { null },
                lineIndex = pm.songDisplayLineIndex.value,
            )
        }
        Presenting.BIBLE -> {
            val verse = pm.selectedVerse.value
            val verses = verse.verseRange.ifBlank { verse.verseNumber.toString() }
            if (verse.bookName.isBlank()) none
            else LiveHistoryEntry(
                contentType = mode.name,
                verseCode = verseCode?.let { (b, c, v) -> "B%03dC%03dV%03d".format(Locale.ROOT, b, c, v) },
                reference = "${verse.bookName} ${verse.chapter}:$verses",
            )
        }
        Presenting.PRESENTATION -> {
            val slide = pm.liveSlide.value
            LiveHistoryEntry(mode.name, fileName = slide?.fileName, slideIndex = slide?.index)
        }
        Presenting.PICTURES -> pm.selectedImagePath.value?.takeIf { it.isNotBlank() }
            ?.let { LiveHistoryEntry(mode.name, fileName = File(it).name) } ?: none
        Presenting.MEDIA -> pm.currentMediaUrl.value.takeIf { it.isNotBlank() }
            ?.let { LiveHistoryEntry(mode.name, fileName = fileNameOf(it)) } ?: none
        else -> LiveHistoryEntry(mode.name)
    }
}

/** The last path segment of a file path or URL, query and fragment dropped. */
private fun fileNameOf(pathOrUrl: String): String =
    pathOrUrl.substringBefore('?').substringBefore('#').trimEnd('/', '\\')
        .substringAfterLast('/').substringAfterLast('\\')
