package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * How the song number is drawn on the **title slide**, which is no longer how it is drawn on the
 * lyric slides.
 *
 * The two shared one set of fields, so a congregation that wants the number small in the top-left
 * corner of every lyric slide and large at the bottom-left of the title slide could have one or the
 * other and not both. They are now separate profiles with separate placement.
 *
 * [SongCreditStyle] rather than a type of its own: it carries exactly the seventeen fields a song
 * element has, minus the position a title-slide number does not need -- where it sits in the flow is
 * [SongSettings.titleSlideNumberBeforeTitle]'s business, and out of the flow it is [corner]'s. The
 * app already converts between that record and its own `SongElementStyle` in both directions.
 *
 * [corner] is [Constants.NONE] by default, which means "in the flow, above or beside the title", and
 * that is what the title slide has always drawn. Set to a corner, the number leaves the flow and is
 * pinned there with [offset] walking it inward, exactly as the lyric slides' cornered number works.
 *
 * Nested in [SongLayoutExtras] rather than added to [SongSettings]: as flat fields this would be
 * thirty-four properties against the two the constructor has left. See [SongOutlines].
 */
@Serializable
data class SongTitleSlideNumber(
    val fullScreen: SongCreditStyle = SongCreditStyle(
        fontSize = DEFAULT_FULL_SCREEN_SIZE,
        horizontalAlignment = Constants.RIGHT,
    ),
    val lowerThird: SongCreditStyle = SongCreditStyle(
        fontSize = DEFAULT_LOWER_THIRD_SIZE,
        horizontalAlignment = Constants.RIGHT,
    ),
    val corner: String = Constants.NONE,
    val lowerThirdCorner: String = Constants.NONE,
    val offset: SongNumberOffset = SongNumberOffset(),
    val lowerThirdOffset: SongNumberOffset = SongNumberOffset(),
) {
    /** The style this output draws the title slide's number with. */
    fun styleFor(lowerThird: Boolean): SongCreditStyle = if (lowerThird) this.lowerThird else fullScreen

    /** [Constants.NONE] while the number stays in the flow with the title. */
    fun cornerFor(lowerThird: Boolean): String = if (lowerThird) lowerThirdCorner else corner

    /** How far inward from [cornerFor] the number is walked. */
    fun offsetFor(lowerThird: Boolean): SongNumberOffset = if (lowerThird) lowerThirdOffset else offset

    companion object {
        /**
         * The lyric-slide number's own defaults, so a fresh install draws the title slide exactly as
         * it did when the two shared one profile. An upgraded document is seeded from whatever it
         * had actually configured -- see `SettingsManager`'s version 16 step.
         */
        const val DEFAULT_FULL_SCREEN_SIZE = 70
        const val DEFAULT_LOWER_THIRD_SIZE = 28
    }
}
