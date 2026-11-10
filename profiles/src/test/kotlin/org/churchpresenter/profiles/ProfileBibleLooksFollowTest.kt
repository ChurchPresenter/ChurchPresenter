@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Translations group's Follow row: whether this output's translation looks stay with their place
 * in the stack or go with the Bible, and the "Applies to" chips that name a look by whichever it is.
 */
class ProfileBibleLooksFollowTest {

    private fun stack(byPlace: Boolean = true) = BibleSettings(
        looksFollowPosition = byPlace,
        translations = listOf(
            BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV"),
            BibleTranslationSettings(fileName = "rst.spb", customAbbreviation = "RST"),
        ),
    )

    private fun SkikoComposeUiTest.shows(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `Bible version writes only the follow switch, and Place puts it back`() =
        profilesTab(profileDocument(bible = stack())) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            val before = get().bible()
            assertTrue(before.looksFollowPosition, "a profile starts with its looks on their places")
            assertTrue(shows("Each place keeps its look when its Bible is changed or swapped"))

            onNodeWithText("Bible version").performScrollTo().performClick()
            waitForIdle()
            assertEquals(before.copy(looksFollowPosition = false), get().bible())
            assertTrue(shows("Each Bible keeps its own look wherever it moves"))

            onNodeWithText("Place").performScrollTo().performClick()
            waitForIdle()
            assertEquals(before, get().bible())
        }

    @Test
    fun `by place the chips name the place and the Bible in it`() =
        profilesTab(profileDocument(bible = stack(byPlace = true))) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            assertTrue(shows("1 · KJV"))
            assertTrue(shows("2 · RST"))
            assertFalse(shows("KJV"), "no chip is named by the Bible alone")
        }

    @Test
    fun `by Bible the chips name the Bible alone`() =
        profilesTab(profileDocument(bible = stack(byPlace = false))) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            assertTrue(shows("KJV"))
            assertFalse(shows("1 · KJV"))
        }

    private fun ownChip(byPlace: Boolean, expected: String) =
        profilesTab(profileDocument(bible = stack(byPlace))) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithTag(translationChipTag(0)).performScrollTo().performClick()
            waitForIdle()
            typeInRow("Size", 50)
            onNodeWithText(expected).assertExists()
        }

    @Test
    fun `by place a look set for one translation is that place's own`() = ownChip(byPlace = true, "Only 1")

    @Test
    fun `by Bible a look set for one translation is that Bible's own`() = ownChip(byPlace = false, "Only KJV")

    @Test
    fun `the group's reset puts the follow switch back with the rest`() {
        val changed = BibleSettings(
            looksFollowPosition = false,
            bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE,
            splitLongVerses = true,
            longVerseWordCount = 12,
        )
        assertTrue(changed.translationsGroupChanged())
        assertFalse(BibleSettings().translationsGroupChanged())
        assertFalse(BibleSettings(longVerseWordCount = 12).translationsGroupChanged(), "the count alone is no change")
        assertEquals(BibleSettings(), changed.withTranslationsGroupReset())
        assertTrue(BibleSettings(looksFollowPosition = false).translationsGroupChanged(), "the switch alone is")
    }
}
