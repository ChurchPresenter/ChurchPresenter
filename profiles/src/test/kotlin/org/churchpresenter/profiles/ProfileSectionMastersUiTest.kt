package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OWN_SECTION
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProfileSection
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.withLinksResolved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A linked profile following another master in some sections, as the Profiles tab shows it: the
 * Sections list on General, the marks naming each section's own master, a section of its own, and
 * how a master followed in one section lists its follower.
 *
 * Sign language follows Sanctuary, except its Bible, which follows Livestream.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileSectionMastersUiTest {

    private val bible = BibleSettings(
        translations = listOf(BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV")),
    )

    private fun doc(sectionMasters: Map<String, String> = mapOf("bible" to "stream")): AppSettings = AppSettings(
        bibleSettings = bible,
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "main", name = "Sanctuary", bibleSettings = bible),
                OutputProfile(id = "sign", name = "Sign language", parentId = "main", sectionMasters = sectionMasters),
                OutputProfile(
                    id = "stream",
                    name = "Livestream",
                    bibleSettings = bible.copy(translations = bible.translations.map { it.copy(textFontSize = 64) }),
                    sttSettings = STTSettings(maxLines = 5),
                ),
            ),
        ).withLinksResolved(),
    )

    private fun SkikoComposeUiTest.select(id: String) = tap(profileRowTag(id))

    /** Picks [label] in the master picker of [section] on General. */
    private fun SkikoComposeUiTest.pickMaster(section: ProfileSection, label: String) {
        openProfilePage(ProfilePage.General)
        tap(sectionMasterTag(section))
        onNode(hasTextExactly(label) and hasAnyAncestor(isPopup())).performClick()
        waitForIdle()
    }

    private fun SkikoComposeUiTest.assertShown(text: String) =
        assertTrue(onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty(), "\"$text\" not shown")

    @Test
    fun `General lists a picker per section, with the main master, the others and Own`() = profilesTab(doc()) { _ ->
        select("sign")
        openProfilePage(ProfilePage.General)
        listOf(ProfileSection.CONTENT, ProfileSection.BIBLE, ProfileSection.SONGS, ProfileSection.DICTIONARY)
            .forEach { assertEquals(1, countTag(sectionMasterTag(it))) }
        // A full screen has no stage layout to follow.
        assertEquals(0, countTag(sectionMasterTag(ProfileSection.STAGE)))
        assertShown("Sanctuary (main)")
        tap(sectionMasterTag(ProfileSection.SONGS))
        listOf("Sanctuary (main)", "Livestream", "Own").forEach {
            onNode(hasTextExactly(it) and hasAnyAncestor(isPopup())).assertExists()
        }
    }

    @Test
    fun `picking a master for a section, then Own, then the main master again`() = profilesTab(doc(emptyMap())) { get ->
        select("sign")
        pickMaster(ProfileSection.CAPTIONS, "Livestream")
        assertEquals(mapOf("captions" to "stream"), get().profile("sign").sectionMasters)
        assertEquals(5, get().profile("sign").sttSettings.maxLines)
        pickMaster(ProfileSection.CAPTIONS, "Own")
        assertEquals(OWN_SECTION, get().profile("sign").sectionMasters["captions"])
        assertEquals(5, get().profile("sign").sttSettings.maxLines)
        pickMaster(ProfileSection.CAPTIONS, "Sanctuary (main)")
        assertTrue(get().profile("sign").sectionMasters.isEmpty())
        assertEquals(STTSettings().maxLines, get().profile("sign").sttSettings.maxLines)
    }

    @Test
    fun `a page's marks name the master of its own section`() = profilesTab(doc()) { get ->
        select("sign")
        openCustomizePane(CustomizePane.BIBLE)
        onNodeWithText("Follows Livestream except 0 settings on this page.", substring = true).assertExists()
        assertTrue(countTag(INHERITED_FRAME_TAG) > 0)
        stepUp("Size")
        assertEquals(66, get().profile("sign").bibleSettings.translations[0].textFontSize)
        assertShown("Livestream: 64")
        assertShown("Revert to Livestream")
        // The side card names each change's own master too, and says the profile has more than one.
        assertShown("Different from its masters")
        // The next page follows the main master.
        openCustomizePane(CustomizePane.SONGS)
        onNodeWithText("Follows Sanctuary except 0 settings on this page.", substring = true).assertExists()
    }

    @Test
    fun `a page of its own has no marks and says so`() = profilesTab(doc(mapOf("bible" to OWN_SECTION))) { get ->
        select("sign")
        openCustomizePane(CustomizePane.BIBLE)
        assertShown("This page is this profile's own.")
        assertEquals(0, countTag(INHERITED_FRAME_TAG))
        assertEquals(0, countTag(ONLY_CHANGES_TAG))
        stepUp("Size")
        assertTrue(get().profile("sign").overrides.isEmpty())
        assertEquals(0, countTag(REVERT_LINK_TAG))
    }

    @Test
    fun `a master followed in one section lists its follower with that section`() = profilesTab(doc()) { _ ->
        select("stream")
        assertShown("Master of 1 linked profiles")
        assertShown("Sign language (Bible)")
        // The follower stays in its main master's block, noting the other master.
        assertShown("+1 master")
    }

    @Test
    fun `Unlink forgets every master, and Undo brings them back`() = profilesTab(doc()) { get ->
        select("sign")
        openProfilePage(ProfilePage.General)
        onAllNodes(hasTestTag(actionKeyTag("Unlink")))[0].performClick()
        waitForIdle()
        assertTrue(get().profile("sign").sectionMasters.isEmpty())
        assertFalse(onAllNodesWithText("+1 master", substring = true).fetchSemanticsNodes().isNotEmpty())
        onNode(hasTextExactly("Undo")).performClick()
        waitForIdle()
        assertEquals(mapOf("bible" to "stream"), get().profile("sign").sectionMasters)
    }
}
