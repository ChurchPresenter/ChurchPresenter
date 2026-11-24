package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/**
 * The height of a chip: [Default] or [Small].
 */
enum class ChipSize { Default, Small }

/**
 * The accent a chip or tag is tinted with: [Neutral] has none; the song sections ([Verse], [Chorus], [Bridge],
 * [Intro], [Voice], [OtherSection]) and content types ([Song], [Bible], [Presentation], [Pictures], [Media],
 * [LowerThird], [Announcement]) use their theme roles; [Live] uses the on-screen red.
 */
enum class ChipColor {
    Neutral,
    Verse,
    Chorus,
    Bridge,
    Intro,
    Voice,
    OtherSection,
    Song,
    Bible,
    Presentation,
    Pictures,
    Media,
    LowerThird,
    Announcement,
    Live,
}

@Immutable
internal data class ChipColors(
    val container: Color,
    val edge: Color,
    val content: Color,
    val sub: Color,
    val ring: Color?,
)
