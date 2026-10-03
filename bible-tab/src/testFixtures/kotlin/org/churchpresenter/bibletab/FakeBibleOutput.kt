package org.churchpresenter.bibletab

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.bible.SelectedVerse

/** A [BibleOutput] that only holds state: the verses on screen and hold mode, set directly. */
class FakeBibleOutput : BibleOutput {
    private val displayed = mutableStateOf<List<SelectedVerse>>(emptyList())
    private val hold = mutableStateOf(false)

    override val displayedVerses: State<List<SelectedVerse>> = displayed
    override val bibleHold: State<Boolean> = hold

    override fun setBibleHold(hold: Boolean) {
        this.hold.value = hold
    }

    fun setDisplayedVerses(verses: List<SelectedVerse>) {
        displayed.value = verses
    }
}
