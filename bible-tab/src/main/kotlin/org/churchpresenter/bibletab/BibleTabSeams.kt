package org.churchpresenter.bibletab

import androidx.compose.runtime.State
import org.churchpresenter.core.models.bible.SelectedVerse

/** What the tab needs of the live output: the verses on screen, and hold mode. The app's `BibleOutput`. */
interface BibleOutput {
    /** What the outputs draw -- driven by a single animation so all windows stay in sync. */
    val displayedVerses: State<List<SelectedVerse>>

    /** Hold mode: while on, a verse chosen is staged and not sent to the outputs. */
    val bibleHold: State<Boolean>

    fun setBibleHold(hold: Boolean)
}

/** Whether the Bible detection engine is up, for the detection panel's status. The app's `BibleEngineStatus`. */
interface BibleEngineStatus {
    val connected: State<Boolean>
    val startFailed: State<Boolean>

    /** Whether the engine's speech-to-text feed is connected, or null until it has said. */
    val engineSttConnected: State<Boolean?>
}

/** Where a verse that went live is counted for the usage report. The app's `BibleVerseStatistics`. */
fun interface BibleVerseStatistics {
    fun recordVerseDisplay(bibleName: String, bookName: String, chapter: Int, verseNumber: Int)
}
