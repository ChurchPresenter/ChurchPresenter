package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

enum class ChipSize { Default, Small }

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
