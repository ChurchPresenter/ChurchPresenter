package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.presenter.numberOffset
import org.churchpresenter.presenter.referenceShiftFor
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileNudgeFieldsTest {

    private fun SkikoComposeUiTest.typeInTagged(tag: String, value: Int) {
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .performScrollTo()
            .performTextReplacement(value.toString())
        waitForIdle()
    }

    @Test
    fun `the song number's offset fields write both axes`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
        typeInTagged("song_number_offset_x", 12)
        typeInTagged("song_number_offset_y", 34)

        val stored = get().song().numberOffset(lowerThird = false)
        assertEquals(12, stored.xPercent)
        assertEquals(34, stored.yPercent)
    }

    @Test
    fun `the reference moves on its own on both axes`() {
        val bible = BibleSettings(
            translations = listOf(BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV")),
        )
        profilesTab(profileDocument(bible = bible)) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_REFERENCE)
            typeInRow("Move X / Y", 30, nth = 0)
            typeInRow("Move X / Y", -15, nth = 1)

            assertEquals(30 to -15, get().bible().translationList()[0].referenceShiftFor(lowerThird = false))
        }
    }
}
