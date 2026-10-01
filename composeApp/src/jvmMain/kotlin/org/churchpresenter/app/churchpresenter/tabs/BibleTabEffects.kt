package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import churchpresenter.composeapp.generated.resources.chapter
import org.churchpresenter.app.churchpresenter.composables.rememberTokenGate
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.app.churchpresenter.viewmodel.getSelectedVerses
import org.churchpresenter.app.churchpresenter.viewmodel.logLiveReference
import org.churchpresenter.app.churchpresenter.viewmodel.updateSettings

/** Reloads on a settings change, resolves a scheduled verse, and takes focus back after a dialog. */
@Composable
internal fun BibleTabScope.BibleTabEffects(
    viewModel: BibleViewModel,
    selectedVerseItem: ScheduleItem.BibleVerseItem?,
    selectedVerseItemVersion: Int,
    dialogDismissSignal: Int,
) {
    val isFirstComposition = remember { mutableStateOf(true) }
    LaunchedEffect(
        appSettings.bibleSettings.storageDirectory,
        translationSelectionKey,
        // A rename changes neither the folder nor the selection, so without it here the view model
        // never hears about one: the modules in memory keep the name they were loaded under, and
        // the verse on screen keeps the abbreviation it was built with until something else forces
        // a reload. That is what made a cleared abbreviation stay on the presentation screen.
        appSettings.bibleSettings.customNameKey(),
        // Likewise: splitting changes neither the folder nor the selection nor a name, so without
        // it here the view model keeps the snapshot it was constructed with and the setting does
        // nothing until the app is restarted. The threshold is a key for the same reason -- moving
        // the slider without crossing its Off stop changes only this number.
        appSettings.bibleSettings.splitLongVerses,
        appSettings.bibleSettings.longVerseWordCount,
    ) {
        if (isFirstComposition.value) {
            isFirstComposition.value = false
        } else {
            viewModel.updateSettings(appSettings)
        }
    }

    /**
     * Puts a schedule item's verse on screen.
     *
     * The push is made here, from the resolution itself, rather than left to
     * `LaunchedEffect(verseSelectionToken)` below: that effect is skipped while a multi-verse
     * selection is live and while split-browse is on — both right for an interactive selection and
     * wrong for an item the operator explicitly clicked — and it only fires while this tab is
     * composed, which it may not be by the time a cold-start Bible load finishes.
     */
    LaunchedEffect(selectedVerseItem, selectedVerseItemVersion) {
        selectedVerseItem?.let { item ->
            val verses = viewModel.resolveVerseSelection(
                bookName = item.bookName,
                chapter = item.chapter,
                verseNumber = item.verseNumber,
                verseRange = item.verseRange,
                bookId = item.bookId,
            )
            if (verses.isEmpty()) {
                CrashReporter.breadcrumb(
                    "Bible schedule item did not resolve to a verse",
                    category = "schedule",
                )
                return@LaunchedEffect
            }
            onVerseSelected(verses)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(dialogDismissSignal) { focusRequester.requestFocus() }
}

/** Keeps the split-browse live panel on the chapter that is live, and sends its key-press steps. */
@Composable
internal fun BibleTabScope.BibleSplitLiveEffects(viewModel: BibleViewModel) {
    LaunchedEffect(displayedVerses, splitBrowseMode) {
        if (!splitBrowseMode || displayedVerses.isEmpty()) return@LaunchedEffect
        val first = displayedVerses.first()
        liveBookName = first.bookName
        liveChapterNum = first.chapter
        liveVerseNumbers = setOf(displayedVerses.first().verseNumber)
        liveNavTargetVerse = liveVerseNumbers.minOrNull() ?: 0
        liveChapterVerses = viewModel.getChapterVerses(first.bookName, first.chapter)
    }

    LaunchedEffect(splitBrowseMode, verses.size) {
        if (!splitBrowseMode) return@LaunchedEffect
        if (liveChapterVerses.isNotEmpty() || displayedVerses.isNotEmpty()) return@LaunchedEffect
        val first = viewModel.getSelectedVerses().firstOrNull() ?: return@LaunchedEffect
        liveBookName = first.bookName
        liveChapterNum = first.chapter
        liveVerseNumbers = setOf(first.verseNumber)
        liveNavTargetVerse = first.verseNumber
        liveChapterVerses = viewModel.getChapterVerses(first.bookName, first.chapter)
    }

    LaunchedEffect(liveNavToken) {
        if (liveNavToken == 0 || liveNavTargetVerse == 0) return@LaunchedEffect
        val verses = viewModel.getVersesForDisplay(liveBookName, liveChapterNum, liveNavTargetVerse)
        if (verses.isNotEmpty()) {
            val primary = verses.first()
            if (!liveNavPageStep) {
                statisticsManager?.recordVerseDisplay(
                    primary.bibleName, primary.bookName, primary.chapter, primary.verseNumber
                )
            }
            onVerseSelected(verses)
            onInstanceLinkSendVerse?.invoke(
                primary.bookName,
                primary.chapter,
                primary.verseNumber,
                primary.verseText,
                primary.verseRange,
            )
            presenterManager?.let { if (it.bibleHold.value) {
                it.setBibleHold(false)
                onInstanceLinkSendBibleHold?.invoke(false)
            } }
            onPresenting(Presenting.BIBLE)
            if (!liveNavPageStep) {
                viewModel.logLiveReference(
                    displayBookIndex = viewModel.selectedBookIndex.value,
                    chapter    = primary.chapter,
                    verseStart = primary.verseNumber,
                    verseEnd   = null,
                    source     = "manual",
                    autoFollow = viewModel.autoFollowEnabled.value,
                )
            }
        }
    }
}

/** Auto-follow, selection publishing, the navigate-away hold, and STT bookkeeping. */
@Composable
internal fun BibleTabScope.BibleSelectionEffects(viewModel: BibleViewModel) {
    val autoFollowLiveToken by viewModel.autoFollowLiveToken

    val autoFollowTokenGate = rememberTokenGate(autoFollowLiveToken)
    LaunchedEffect(autoFollowLiveToken) {
        if (!autoFollowTokenGate.consume()) return@LaunchedEffect
        goLiveWithHistory(viewModel, 
            source = viewModel.autoFollowLiveSource.value,
            matchType = viewModel.autoFollowLiveMatchType.value,
        )
    }

    LaunchedEffect(verseSelectionToken) {

        if (viewModel.multiVerseEnabled.value && currentIsPresenting) return@LaunchedEffect

        if (splitBrowseMode) return@LaunchedEffect
        if (verses.isNotEmpty() && selectedVerseIndex >= 0 && selectedVerseIndex < verses.size) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) {
                onVerseSelected(selectedVerses)

                if (currentIsPresenting && autoFollowLiveToken == autoFollowTokenGate.lastHandled) {
                    val primary = selectedVerses.first()
                    viewModel.logLiveReference(
                        displayBookIndex = viewModel.selectedBookIndex.value,
                        chapter    = primary.chapter,
                        verseStart = primary.verseNumber,
                        verseEnd   = null,
                        source     = "manual",
                        autoFollow = viewModel.autoFollowEnabled.value,
                    )
                }
            }
        }
    }

    LaunchedEffect(verses.size) {
        if (!currentIsPresenting && !splitBrowseMode && verses.isNotEmpty()) {
            val selectedVerses = viewModel.getSelectedVerses()
            if (selectedVerses.isNotEmpty()) onVerseSelected(selectedVerses)
        }
    }

    val prevBookRef = remember { mutableStateOf(selectedBookIndex) }
    val prevChapterRef = remember { mutableStateOf(selectedChapter) }
    LaunchedEffect(selectedBookIndex, selectedChapter) {
        val bookChanged = selectedBookIndex != prevBookRef.value
        val chapterChanged = selectedChapter != prevChapterRef.value
        prevBookRef.value = selectedBookIndex
        prevChapterRef.value = selectedChapter
        val wasSequentialAdvance = viewModel.consumeSequentialChapterAdvance()
        val navigatedAway = bookChanged || chapterChanged
        val autoHoldApplies = !splitBrowseMode && currentIsPresenting && !wasSequentialAdvance
        if (navigatedAway && autoHoldApplies) {
            presenterManager?.setBibleHold(true)
        }
    }

    LaunchedEffect(detectedReferences.size) { selectedDetectionIdx = 0 }

    LaunchedEffect(sttConnected) {
        if (sttConnected) {
            val url = appSettings.sttSettings.serverUrl
            if (appSettings.sttSettings.lastConnectedUrl != url) {
                onSettingsChange { it.copy(sttSettings = it.sttSettings.copy(lastConnectedUrl = url)) }
            }
        }
    }
}
