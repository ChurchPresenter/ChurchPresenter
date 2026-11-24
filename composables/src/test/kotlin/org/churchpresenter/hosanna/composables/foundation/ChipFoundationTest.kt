package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.composables.chip.chipPadding
import org.churchpresenter.hosanna.composables.defaults.ChipDefaults
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.hosanna.composables.model.ChipColors
import org.churchpresenter.hosanna.composables.model.ChipSize
import org.churchpresenter.hosanna.composables.testing.LightThemeColors
import org.churchpresenter.hosanna.composables.testing.DarkThemeColors

class ChipFoundationTest {

    private val c = LightThemeColors

    @Test
    fun `every chip color is a theme role, neutral has none`() {
        listOf(c, DarkThemeColors).forEach { colors ->
            val expected = mapOf(
                ChipColor.Verse to colors.chordVerse,
                ChipColor.Chorus to colors.chordChorus,
                ChipColor.Bridge to colors.chordBridge,
                ChipColor.Intro to colors.sectionIntro,
                ChipColor.Voice to colors.sectionVoice,
                ChipColor.OtherSection to colors.sectionOther,
                ChipColor.Song to colors.contentSongs,
                ChipColor.Bible to colors.contentBible,
                ChipColor.Presentation to colors.contentPresentation,
                ChipColor.Pictures to colors.contentPictures,
                ChipColor.Media to colors.contentMedia,
                ChipColor.LowerThird to colors.contentLowerThird,
                ChipColor.Announcement to colors.contentAnnouncement,
                ChipColor.Live to colors.live,
            )
            assertNull(colors.accent(ChipColor.Neutral))
            expected.forEach { (chip, role) -> assertEquals(role, colors.accent(chip), "$chip") }
            assertEquals(ChipColor.entries.size - 1, expected.size)
        }
    }

    @Test
    fun `neutral, selected and colored chips rest on their roles`() {
        assertEquals(
            ChipColors(c.secondaryContainer, c.outlineVariant, c.onSurface, c.onSurfaceVariant, null),
            c.restChipColors(ChipColor.Neutral, selected = false),
        )
        assertEquals(
            ChipColors(
                c.primaryContainer,
                c.primary.copy(alpha = HosannaStateAlpha.CONTENT_TYPE_EDGE),
                c.onPrimaryContainer,
                c.onPrimaryContainer,
                null,
            ),
            c.restChipColors(ChipColor.Chorus, selected = true),
        )
        assertEquals(
            ChipColors(
                c.chordVerse.copy(alpha = HosannaStateAlpha.CONTENT_TYPE_CONTAINER),
                c.chordVerse.copy(alpha = HosannaStateAlpha.CONTENT_TYPE_EDGE),
                c.chordVerse,
                c.chordVerse,
                null,
            ),
            c.restChipColors(ChipColor.Verse, selected = false),
        )
    }

    @Test
    fun `states lay the content color over the container`() {
        ChipColor.entries.forEach { color ->
            listOf(false, true).forEach { selected ->
                val rest = c.restChipColors(color, selected)
                fun layer(alpha: Float) = rest.content.copy(alpha = alpha).compositeOver(rest.container)
                assertEquals(rest, c.chipColors(color, selected, ButtonState.Rest))
                assertEquals(rest, c.chipColors(color, selected, ButtonState.Loading))
                assertEquals(
                    rest.copy(container = layer(HosannaStateAlpha.HOVER)),
                    c.chipColors(color, selected, ButtonState.Hovered),
                )
                assertEquals(
                    rest.copy(container = layer(HosannaStateAlpha.PRESSED)),
                    c.chipColors(color, selected, ButtonState.Pressed),
                )
                assertEquals(
                    rest.copy(
                        container = layer(HosannaStateAlpha.FOCUS),
                        ring = c.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
                    ),
                    c.chipColors(color, selected, ButtonState.Focused),
                )
            }
        }
    }

    @Test
    fun `a disabled chip looks the same whatever its color`() {
        val ink = c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        val expected = ChipColors(
            c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER),
            Color.Transparent,
            ink,
            ink,
            null,
        )
        ChipColor.entries.forEach { color ->
            assertEquals(expected, c.chipColors(color, false, ButtonState.Disabled))
            assertEquals(expected, c.chipColors(color, true, ButtonState.Disabled))
        }
    }

    @Test
    fun `padding is tighter on the icon side and before the remove button`() {
        val d = ChipDefaults.Default
        assertEquals(PaddingValues(start = d.padding, end = d.padding), chipPadding(ChipSize.Default, leading = false))
        assertEquals(
            PaddingValues(start = d.iconSidePadding, end = d.padding),
            chipPadding(ChipSize.Default, leading = true),
        )
        val s = ChipDefaults.Small
        assertEquals(
            PaddingValues(start = s.iconSidePadding, end = ChipDefaults.RemoveGap),
            chipPadding(ChipSize.Small, leading = true, end = false),
        )
    }
}
