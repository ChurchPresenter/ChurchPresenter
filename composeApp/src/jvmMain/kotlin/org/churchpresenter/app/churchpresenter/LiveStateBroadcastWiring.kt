package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.churchpresenter.bible.Bible
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.hasAudienceOutput
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

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
            if (pm.presentingMode.value != Presenting.NONE &&
                hasAudienceOutput(
                    appSettings().projectionSettings.screenAssignments,
                    screenCountForUsage,
                    deckLinkCountForUsage,
                )
            ) {
                UsageEvents.recordOncePerInstall(UsageEvent.FIRST_LIVE_ON_SCREEN)
            }
            val liveVerse = pm.selectedVerse.value
            val verseCode = liveVerseCode(
                source = source,
                bookName = liveVerse.bookName,
                chapter = liveVerse.chapter,
                verseNumber = liveVerse.verseNumber,
                bookIdByName = { name -> primaryBible()?.getBookIdByName(name) },
                codeReference = { bookId, chapter, verse ->
                    primaryBible()?.getCodeReference(bookId, chapter, verse)
                },
            )
            companionServer.updateLiveState(
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
                songLineIndex = livePositionOrNull(source, Presenting.LYRICS, pm.songDisplayLineIndex.value)
            )
        }
    }
}
