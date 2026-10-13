package org.churchpresenter.bibletab

import androidx.compose.runtime.State

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
