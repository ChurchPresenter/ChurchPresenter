package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileBoxAndTextRowsTest {

    private val stack = BibleSettings(
        translations = listOf(
            BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV"),
            BibleTranslationSettings(fileName = "rst.spb", customAbbreviation = "RST"),
        ),
    )

    @Test
    fun `a band's box takes its rectangle, the whole screen and one box for both languages`() = profilesTab(
        profileDocument(mode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, bible = stack),
    ) { get ->
        openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
        onNodeWithTag(translationChipTag(0)).performScrollTo().performClick()
        waitForIdle()
        toggleCheckbox("Text box")
        typeInRow("Box position", 21, nth = 1)
        typeInRow("Box position", 31, nth = 2)
        typeInRow("Box position", 41, nth = 3)
        chooseSegment("Whole screen")
        chooseSegment("Share one box")
        val bible = get().bible()
        val box = bible.textBoxes.boxAt(textBoxKey("TEXT", lowerThird = true, language = "kjv.spb"))
        assertEquals(21f, box.yPercent)
        assertEquals(31f, box.widthPercent)
        assertEquals(41f, box.heightPercent)
        assertTrue(bible.textBoxOptions.lowerThirdWholeScreen)
        assertTrue(bible.textBoxOptions.sharedLanguageBox)
    }

    @Test
    fun `the subtitles' face, color, italic, underline and shadow are written`() {
        val family = uniquelyNamedFont()
        val media = MediaSettings(
            fontType = SENTINEL_FONT,
            textColor = "#ABABAB",
            shadow = true,
            shadowColor = "#0A0A0A",
            shadowSize = 77,
            shadowOpacity = 66,
        )
        profilesTab(profileDocument(profile = OutputProfile(mediaSettings = media))) { get ->
            openCustomizePane(CustomizePane.SUBTITLES)
            pickFont(SENTINEL_FONT, family)
            recolor("#ABABAB", "#BCBCBC")
            inRow("Style", hasClickAction(), 1).performClick()
            waitForIdle()
            inRow("Style", hasClickAction(), 2).performClick()
            waitForIdle()
            recolor("#0A0A0A", "#0B0B0B")
            retypeNumberField(77, 88)
            retypeNumberField(66, 55)
            val m = get().profile().mediaSettings
            assertEquals(family, m.fontType)
            assertEquals("#BCBCBC", m.textColor)
            assertTrue(m.italic)
            assertTrue(m.underline)
            assertEquals("#0B0B0B", m.shadowColor)
            assertEquals(88, m.shadowSize)
            assertEquals(55, m.shadowOpacity)
        }
    }
}
