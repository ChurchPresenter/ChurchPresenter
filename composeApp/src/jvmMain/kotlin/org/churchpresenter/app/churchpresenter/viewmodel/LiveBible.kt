package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting

/** The verse the operator chose, and the verses the outputs are fading between. Part of [PresenterManager]. */
interface LiveBible {
    val selectedVerse: State<SelectedVerse>
    val selectedVerses: State<List<SelectedVerse>>

    /** What the outputs draw -- driven by a single animation so all windows stay in sync. */
    val displayedVerses: State<List<SelectedVerse>>

    /**
     * The verse after whatever is displayed, for the Stage Monitor's "Next" zone. Distinct from
     * [displayedVerses], which pairs the primary and secondary *language* of the same verse.
     */
    val nextVerses: State<List<SelectedVerse>>
    val bibleTransitionAlpha: State<Float>

    /** What a crossfade is leaving, drawn under the new verse while both are visible. */
    val previousDisplayedVerses: State<List<SelectedVerse>>
    val previousBibleAlpha: State<Float>

    /** Hold mode: while on, a verse chosen is staged and not sent to the outputs. */
    val bibleHold: State<Boolean>

    fun setSelectedVerse(verse: SelectedVerse)
    fun setSelectedVerses(verses: List<SelectedVerse>)
    fun setDisplayedVerses(verses: List<SelectedVerse>)
    fun setNextVerses(verses: List<SelectedVerse>)
    fun setBibleTransitionAlpha(alpha: Float)
    fun setPreviousDisplayedVerses(verses: List<SelectedVerse>)
    fun setPreviousBibleAlpha(alpha: Float)
    fun setBibleHold(hold: Boolean)
}

internal class LiveBibleState(private val context: PresenterContext) : LiveBible {

    private val _selectedVerse = mutableStateOf(SelectedVerse())
    override val selectedVerse: State<SelectedVerse> = _selectedVerse

    private val _selectedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    override val selectedVerses: State<List<SelectedVerse>> = _selectedVerses

    private val _displayedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    override val displayedVerses: State<List<SelectedVerse>> = _displayedVerses

    private val _nextVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    override val nextVerses: State<List<SelectedVerse>> = _nextVerses

    private val _bibleTransitionAlpha = mutableStateOf(1f)
    override val bibleTransitionAlpha: State<Float> = _bibleTransitionAlpha

    private val _previousDisplayedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    override val previousDisplayedVerses: State<List<SelectedVerse>> = _previousDisplayedVerses

    private val _previousBibleAlpha = mutableStateOf(0f)
    override val previousBibleAlpha: State<Float> = _previousBibleAlpha

    private val _bibleHold = mutableStateOf(false)
    override val bibleHold: State<Boolean> = _bibleHold

    override fun setSelectedVerse(verse: SelectedVerse) {
        _selectedVerse.value = verse
        context.notify(Presenting.BIBLE)
    }

    override fun setSelectedVerses(verses: List<SelectedVerse>) {
        _selectedVerses.value = verses
        if (verses.isNotEmpty()) {
            _selectedVerse.value = verses.first()
        }
        context.notify(Presenting.BIBLE)
    }

    override fun setDisplayedVerses(verses: List<SelectedVerse>) {
        _displayedVerses.value = verses
    }

    override fun setNextVerses(verses: List<SelectedVerse>) {
        _nextVerses.value = verses
    }

    override fun setBibleTransitionAlpha(alpha: Float) {
        _bibleTransitionAlpha.value = alpha
    }

    override fun setPreviousDisplayedVerses(verses: List<SelectedVerse>) {
        _previousDisplayedVerses.value = verses
    }

    override fun setPreviousBibleAlpha(alpha: Float) {
        _previousBibleAlpha.value = alpha
    }

    override fun setBibleHold(hold: Boolean) {
        _bibleHold.value = hold
    }

    /** Puts back what a snapshot held, without reporting it -- see [PresenterManager.restoreLiveState]. */
    fun restore(snapshot: PresenterManager.LiveStateSnapshot) {
        _selectedVerse.value = snapshot.selectedVerse
        _selectedVerses.value = snapshot.selectedVerses
        _displayedVerses.value = snapshot.displayedVerses
    }
}
