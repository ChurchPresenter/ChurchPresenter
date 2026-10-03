package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.sharedui.models.Presenting

/** The lyric section the operator chose, and the one the outputs draw. Part of [PresenterManager]. */
interface LiveSongs {
    val lyricSection: State<LyricSection>

    /**
     * Bumped on every [setLyricSection], so Compose sees a change even when the section is
     * structurally identical -- the same song and section clicked twice.
     */
    val lyricSectionVersion: State<Int>

    /** What the outputs draw, moved only together with [displayedSongPosition]. */
    val displayedLyricSection: State<LyricSection>

    /** The outputs' place in the song, moved only together with the displayed section. */
    val displayedSongPosition: State<DisplayedSongPosition>
    val songTransitionAlpha: State<Float>

    /** What a crossfade is leaving. */
    val previousDisplayedLyricSection: State<LyricSection>
    val previousSongAlpha: State<Float>
    val songDisplayLineIndex: State<Int>
    val songDisplaySectionIndex: State<Int>

    /** Every section of the song chosen -- what auto-fit sizes against. */
    val allLyricSections: State<List<LyricSection>>

    fun setLyricSection(section: LyricSection)

    /**
     * Puts [section] on the outputs at [position] -- by default the operator's position as it
     * stands, which is where a section pushed after its position was set belongs.
     */
    fun setDisplayedLyricSection(section: LyricSection, position: DisplayedSongPosition = liveSongPosition())

    /** Moves the outputs to [position] within the section already displayed. */
    fun setDisplayedSongPosition(position: DisplayedSongPosition)

    /** The operator's place in the song, as the outputs will show it once the section follows. */
    fun liveSongPosition(): DisplayedSongPosition
    fun setSongTransitionAlpha(alpha: Float)
    fun setPreviousDisplayedLyricSection(section: LyricSection)
    fun setPreviousSongAlpha(alpha: Float)
    fun setSongDisplayLineIndex(index: Int)
    fun setSongDisplaySectionIndex(index: Int)
    fun setAllLyricSections(sections: List<LyricSection>)
}

internal class LiveSongsState(private val context: PresenterContext) : LiveSongs {

    private val _lyricSection = mutableStateOf(LyricSection())
    override val lyricSection: State<LyricSection> = _lyricSection

    private val _lyricSectionVersion = mutableStateOf(0)
    override val lyricSectionVersion: State<Int> = _lyricSectionVersion

    private val _displayedLyricSection = mutableStateOf(LyricSection())
    override val displayedLyricSection: State<LyricSection> = _displayedLyricSection

    private val _displayedSongPosition = mutableStateOf(DisplayedSongPosition())
    override val displayedSongPosition: State<DisplayedSongPosition> = _displayedSongPosition

    private val _songTransitionAlpha = mutableStateOf(1f)
    override val songTransitionAlpha: State<Float> = _songTransitionAlpha

    private val _previousDisplayedLyricSection = mutableStateOf(LyricSection())
    override val previousDisplayedLyricSection: State<LyricSection> = _previousDisplayedLyricSection

    private val _previousSongAlpha = mutableStateOf(0f)
    override val previousSongAlpha: State<Float> = _previousSongAlpha

    private val _songDisplayLineIndex = mutableStateOf(-1)
    override val songDisplayLineIndex: State<Int> = _songDisplayLineIndex

    private val _songDisplaySectionIndex = mutableStateOf(-1)
    override val songDisplaySectionIndex: State<Int> = _songDisplaySectionIndex

    private val _allLyricSections = mutableStateOf<List<LyricSection>>(emptyList())
    override val allLyricSections: State<List<LyricSection>> = _allLyricSections

    override fun setLyricSection(section: LyricSection) {
        _lyricSection.value = section
        _lyricSectionVersion.value++
        context.notify(Presenting.LYRICS)
    }

    override fun setDisplayedLyricSection(section: LyricSection, position: DisplayedSongPosition) {
        _displayedLyricSection.value = section
        _displayedSongPosition.value = position
    }

    override fun setDisplayedSongPosition(position: DisplayedSongPosition) {
        _displayedSongPosition.value = position
    }

    override fun liveSongPosition(): DisplayedSongPosition =
        DisplayedSongPosition(_allLyricSections.value, _songDisplaySectionIndex.value, _songDisplayLineIndex.value)

    override fun setSongTransitionAlpha(alpha: Float) {
        _songTransitionAlpha.value = alpha
    }

    override fun setPreviousDisplayedLyricSection(section: LyricSection) {
        _previousDisplayedLyricSection.value = section
    }

    override fun setPreviousSongAlpha(alpha: Float) {
        _previousSongAlpha.value = alpha
    }

    override fun setSongDisplayLineIndex(index: Int) {
        _songDisplayLineIndex.value = index
    }

    override fun setSongDisplaySectionIndex(index: Int) {
        _songDisplaySectionIndex.value = index
    }

    override fun setAllLyricSections(sections: List<LyricSection>) {
        _allLyricSections.value = sections
    }

    /** Puts back what a snapshot held, without reporting it -- see [PresenterManager.restoreLiveState]. */
    fun restore(snapshot: PresenterManager.LiveStateSnapshot) {
        _lyricSection.value = snapshot.lyricSection
        _lyricSectionVersion.value++
        _allLyricSections.value = snapshot.allLyricSections
        _songDisplaySectionIndex.value = snapshot.songDisplaySectionIndex
        _songDisplayLineIndex.value = snapshot.songDisplayLineIndex
        setDisplayedLyricSection(snapshot.displayedLyricSection, liveSongPosition())
    }
}
