package org.churchpresenter.bibletab


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import org.churchpresenter.strings.generated.resources.book
import org.churchpresenter.strings.generated.resources.chapter
import org.churchpresenter.strings.generated.resources.scope
import org.churchpresenter.strings.generated.resources.verse
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.SystemClipboard

/* What the Bible tab's clicks do, pulled out of the browser pane's long argument list. */

/** A verse's cross-reference chip: select the verse, then open or close its popover. */
internal fun BibleTabScope.refsChipClicked(viewModel: BibleViewModel, index: Int) {
    val verseText = filteredVerses.getOrNull(index)
    val realIndex = verseText?.let { verses.indexOf(it) } ?: -1
    if (realIndex >= 0) viewModel.selectVerse(realIndex)
    crossRefs.restartFrom()
    val canonical = verseText?.let(::verseNumberOf)
        ?.let { viewModel.canonicalRefForDisplay(selectedBookIndex, selectedChapter, it) }
        ?.let { (book, chapter, verse) -> verse?.let { Triple(book, chapter, it) } }
    if (crossRefsDocked || canonical == null || crossRefs.popoverIndex == index) {
        crossRefs.closePopover()
    } else {
        crossRefs.popoverIndex = index
        crossRefs.popoverAnchor = canonical
        crossRefs.popoverLabel = viewModel
            .moduleRefFor(canonical.first, canonical.second, canonical.third)
            ?.let { formatCrossRefLabel(it.abbreviation, it.chapter, it.verse, null) }
            ?: ""
    }
    focusRequester.requestFocus()
}

internal fun BibleTabScope.selectFilteredBook(viewModel: BibleViewModel, index: Int) {
    filteredBooks.getOrNull(index)?.let {
        val realIndex = books.indexOf(it)
        if (realIndex >= 0) viewModel.selectBook(realIndex)
    }
}

internal fun BibleTabScope.clickFilteredVerse(viewModel: BibleViewModel, index: Int) {
    filteredVerses.getOrNull(index)?.let {
        val realIndex = verses.indexOf(it)
        if (realIndex >= 0) viewModel.selectVerse(realIndex)
    }
    crossRefs.restartFrom()
    crossRefs.closePopover()
    focusRequester.requestFocus()
}

internal fun BibleTabScope.copySelectedVerse() {
    val verseStr = verses.getOrNull(selectedVerseIndex) ?: ""
    val bookName = books.getOrNull(selectedBookIndex) ?: ""
    val reference = formatVerseReference(verseStr, bookName, selectedChapter)
    SystemClipboard.copy("$reference\n${verseTextOf(verseStr)}")
}

internal fun BibleTabScope.scheduleCurrentVerse(viewModel: BibleViewModel) {
    viewModel.addCurrentVerseToSchedule { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
        onAddToSchedule?.invoke(bookName, chapter, verseNumber, verseText, verseRange, bookId)
    }
    focusRequester.requestFocus()
}

/** A verse clicked in the split-browse live panel goes straight to the output. */
internal fun BibleTabScope.liveVerseClicked(viewModel: BibleViewModel, verseNum: Int) {
    scope.launch {
        val shown = viewModel.getVersesForDisplay(liveBookName, liveChapterNum, verseNum)
        if (shown.isNotEmpty()) {
            val primary = shown.first()
            verseStatistics?.recordVerseDisplay(
                primary.bibleName,
                primary.bookName,
                primary.chapter,
                primary.verseNumber,
            )
            onVerseSelected(shown)
            onInstanceLinkSendVerse?.invoke(
                primary.bookName,
                primary.chapter,
                primary.verseNumber,
                primary.verseText,
                primary.verseRange,
            )
            bibleOutput?.let { if (it.bibleHold.value) {
                it.setBibleHold(false)
                onInstanceLinkSendBibleHold?.invoke(false)
            } }
            onPresenting(Presenting.BIBLE)
            viewModel.logLiveReference(LiveReference(
                displayBookIndex = viewModel.displayIndexForBookName(liveBookName)
                    .takeIf { it >= 0 } ?: viewModel.selectedBookIndex.value,
                chapter    = primary.chapter,
                verseStart = primary.verseNumber,
                verseEnd   = null,
                source     = "manual",
                autoFollow = viewModel.autoFollowEnabled.value,
            ))
            viewModel.canonicalRefForBookName(
                liveBookName, primary.chapter, primary.verseNumber,
            )?.let(crossRefs::anchorLiveVerse)
        }
    }
}

internal fun BibleTabScope.toggleCrossReferences() {
    onSettingsChange { s -> withBibleCrossReferencePanel(s, !crossRefsDocked) }

    crossRefs.popoverIndex = -1
    crossRefs.popoverAnchor = null
    focusRequester.requestFocus()
}

internal fun BibleTabScope.toggleHoldLive() {
    val next = !(bibleOutput?.bibleHold?.value ?: false)
    bibleOutput?.setBibleHold(next)
    onInstanceLinkSendBibleHold?.invoke(next)
    focusRequester.requestFocus()
}

internal fun BibleTabScope.toggleStt() {
    if (sttConnected) sttManager?.disconnect()
    else sttManager?.connect(appSettings.sttSettings.serverUrl)
    focusRequester.requestFocus()
}
