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
