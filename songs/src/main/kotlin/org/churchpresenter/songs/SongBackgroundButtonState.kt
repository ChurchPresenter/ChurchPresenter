package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongBackground

/**
 * What the song editor hands its Background button: the backgrounds of whatever the button is
 * scoped to -- the song itself, or one of its sections -- and the edits the button can make.
 *
 * The button is drawn by the app (`:profiles`' `SongBackgroundButton`), which `:songs` does not
 * depend on, so [EditSongDialog] takes it as a slot and passes it one of these.
 */
interface SongBackgroundButtonState {
    val background: SongBackground
    val lowerThirdBackground: SongBackground
    val expanded: Boolean

    /** The first line the audience would read, for the preview to sit behind. */
    val sampleLine: String

    /** "Whole song", then each section's name; [scopeIndex] is the one being edited. */
    val scopes: List<String>
    val scopeIndex: Int

    /** Whether applying to the song book is offered: only song-wide, and only with a book named. */
    val canApplyToSongbook: Boolean

    fun setExpanded(expanded: Boolean)
    fun setBackground(next: SongBackground)
    fun setLowerThirdBackground(next: SongBackground)
    fun applyToSongbook()
    fun setScope(index: Int)
}

/** The editor's side of [SongBackgroundButtonState], read live from [state]. */
internal class EditorBackgroundButtonState(
    private val state: EditSongState,
    slots: List<SectionBackgroundSlot>,
    override val scopes: List<String>,
    private val onApplyBackgroundToSongbook: ((String, SongBackground, SongBackground) -> Unit)?,
) : SongBackgroundButtonState {
    override val scopeIndex: Int = state.backgroundScope.coerceIn(0, slots.size)
    private val scoped = slots.getOrNull(scopeIndex - 1)
        ?: SectionBackgroundSlot("", -1, state.background, state.lowerThirdBackground)

    override val background: SongBackground get() = scoped.background
    override val lowerThirdBackground: SongBackground get() = scoped.lowerThirdBackground
    override val expanded: Boolean get() = state.backgroundPanelOpen
    override val sampleLine: String = firstLyricLine(state.lyrics.text)

    // Applying to a songbook is a song-wide act, so it is offered only while the song itself is
    // what is being edited.
    override val canApplyToSongbook: Boolean =
        onApplyBackgroundToSongbook != null && state.songbook.isNotBlank() && scopeIndex == 0

    override fun setExpanded(expanded: Boolean) {
        state.backgroundPanelOpen = expanded
    }

    override fun setBackground(next: SongBackground) = state.setBackground(scopeIndex, next, lowerThird = false)

    override fun setLowerThirdBackground(next: SongBackground) =
        state.setBackground(scopeIndex, next, lowerThird = true)

    override fun applyToSongbook() {
        onApplyBackgroundToSongbook?.invoke(state.songbook, state.background, state.lowerThirdBackground)
    }

    override fun setScope(index: Int) {
        state.backgroundScope = index
    }
}
