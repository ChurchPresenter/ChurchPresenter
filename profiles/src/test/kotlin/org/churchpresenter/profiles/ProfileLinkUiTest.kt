package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withLinksResolved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Linked profiles as the Profiles tab shows them: where a value comes from on every row, how a
 * follower's own values are listed, reverted and edited in place, and how profiles are linked,
 * unlinked and made.
 *
 * Sanctuary is the master; Youth night follows it with three values of its own (KJV's size, Q&A
 * off and no fade-in), Easter follows it with none, and Livestream follows nothing.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileLinkUiTest {

    private val bible = BibleSettings(
        translations = listOf(
            BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV"),
            BibleTranslationSettings(fileName = "rst.spb", customAbbreviation = "RST"),
        ),
    )

    private val kjvSize = "bibleSettings.translations[kjv.spb].textFontSize"

    private fun doc(extraYouthOverrides: Map<String, (OutputProfile) -> OutputProfile> = emptyMap()): AppSettings {
        var youth = OutputProfile(
            id = "youth", name = "Youth night", parentId = "main", showQA = false,
            bibleSettings = bible.copy(
                translations = bible.translations.map {
                    if (it.fileName == "kjv.spb") it.copy(textFontSize = 50) else it
                },
                fadeIn = false,
            ),
            overrides = setOf(kjvSize, "showQA", "bibleSettings.fadeIn"),
        )
        extraYouthOverrides.forEach { (path, change) -> youth = change(youth).copy(overrides = youth.overrides + path) }
        return AppSettings(
            bibleSettings = bible,
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(
                    OutputProfile(id = "main", name = "Sanctuary", bibleSettings = bible),
                    youth,
                    OutputProfile(id = "easter", name = "Easter", parentId = "main"),
                    OutputProfile(id = "stream", name = "Livestream", showQA = false, bibleSettings = bible),
                ),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = "main")),
            ).withLinksResolved(),
        )
    }

    private fun SkikoComposeUiTest.select(id: String) = tap(profileRowTag(id))

    /** [text] is on screen at least once -- the header and the Linking card both say how a profile is linked. */
    private fun SkikoComposeUiTest.assertShown(text: String) {
        assertTrue(onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(), "\"$text\" is not shown")
    }

    private fun SkikoComposeUiTest.actionKey(label: String) {
        onAllNodes(hasTestTag(actionKeyTag(label)))[0].performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `the header says how each profile is linked`() = profilesTab(doc()) { _ ->
        assertShown("Master of 2 linked profiles")
        select("youth")
        assertShown("Linked to Sanctuary")
        select("stream")
        assertShown("Standalone profile")
    }

    @Test
    fun `a follower's page marks inherited values dashed and its own with the master's value`() =
        profilesTab(doc()) { _ ->
            select("youth")
            openCustomizePane(CustomizePane.BIBLE)
            assertEquals(1, countTag(LINK_BANNER_TAG))
            onNodeWithText("Follows Sanctuary except 2 settings on this page.", substring = true).assertExists()
            assertTrue(countTag(INHERITED_FRAME_TAG) > 0)
            assertTrue(countTag(OVERRIDE_DOT_TAG) >= 1)
            assertShown("Sanctuary: 70")
            // The section list counts them per page.
            assertTrue(onAllNodes(hasTextExactly("2") and hasAnyAncestor(hasTestTag(CHANGES_CHIP_TAG)),
                    useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty())
        }

    @Test
    fun `Only changes hides every row the follower takes from its master`() = profilesTab(doc()) { _ ->
        select("youth")
        openCustomizePane(CustomizePane.BIBLE)
        tap(ONLY_CHANGES_TAG)
        assertEquals(0, countTag(INHERITED_FRAME_TAG))
        assertTrue(countTag(OVERRIDE_DOT_TAG) >= 1)
        tap(ONLY_CHANGES_TAG)
        assertTrue(countTag(INHERITED_FRAME_TAG) > 0)
    }

    @Test
    fun `Revert on a row and on a group gives values back to the master`() = profilesTab(doc()) { get ->
        select("youth")
        openCustomizePane(CustomizePane.BIBLE)
        onAllNodes(hasTestTag(REVERT_LINK_TAG), useUnmergedTree = true)[0].performScrollTo().performClick()
        waitForIdle()
        assertFalse(kjvSize in get().profile("youth").overrides)
        assertEquals(70, get().profile("youth").bibleSettings.translations[0].textFontSize)
        onAllNodes(hasTextExactly("Revert to Sanctuary"))[0].performScrollTo().performClick()
        waitForIdle()
        assertFalse("bibleSettings.fadeIn" in get().profile("youth").overrides)
        assertTrue(get().profile("youth").bibleSettings.fadeIn)
    }

    @Test
    fun `an edit makes a value a follower's own, and a master's edit reaches its followers`() =
        profilesTab(doc()) { get ->
            select("youth")
            contentSwitch("Media").performClick()
            waitForIdle()
            assertTrue("showMedia" in get().profile("youth").overrides)
            select("main")
            contentSwitch("Pictures/Presentation").performClick()
            waitForIdle()
            assertFalse(get().profile("easter").showPictures)
            assertFalse(get().profile("youth").showPictures)
        }

    @Test
    fun `the differences card edits and reverts each value in place`() = profilesTab(doc()) { get ->
        select("youth")
        val qaRow = hasAnyAncestor(hasTestTag(changeRowTag("showQA")))
        onAllNodes(isToggleable() and qaRow, useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertTrue(get().profile("youth").showQA)
        val sizeRow = hasAnyAncestor(hasTestTag(changeRowTag(kjvSize)))
        onAllNodes(hasContentDescription("Increment") and sizeRow, useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertEquals(51, get().profile("youth").bibleSettings.translations[0].textFontSize)
        onAllNodes(hasTestTag(REVERT_LINK_TAG) and sizeRow, useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertFalse(kjvSize in get().profile("youth").overrides)
    }

    @Test
    fun `a long list of differences shows six, then the rest on asking`() = profilesTab(
        doc(
            listOf("showPictures", "showMedia", "showWebsite", "showCanvas", "showStreaming").associateWith { path ->
                { p: OutputProfile ->
                    when (path) {
                        "showPictures" -> p.copy(showPictures = false)
                        "showMedia" -> p.copy(showMedia = false)
                        "showWebsite" -> p.copy(showWebsite = false)
                        "showCanvas" -> p.copy(showCanvas = false)
                        else -> p.copy(showStreaming = false)
                    }
                }
            },
        ),
    ) { _ ->
        select("youth")
        assertEquals(6, onAllNodes(hasTestTag(REVERT_LINK_TAG) and hasAnyAncestor(hasTestTag(CONTEXT_CARD_TAG)),
                useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        tap(linkTag("Show 2 more"))
        assertEquals(8, onAllNodes(hasTestTag(REVERT_LINK_TAG) and hasAnyAncestor(hasTestTag(CONTEXT_CARD_TAG)),
                useUnmergedTree = true)
            .fetchSemanticsNodes().size)
    }

    @Test
    fun `Unlink keeps every value and Undo puts the link back`() = profilesTab(doc()) { get ->
        select("youth")
        openCustomizePane(CustomizePane.BIBLE)
        actionKey("Unlink")
        assertNull(get().profile("youth").parentId)
        assertFalse(get().profile("youth").showQA)
        onNodeWithText("Unlinked from Sanctuary.", substring = true).assertExists()
        tap(linkTag("Undo"))
        assertEquals("main", get().profile("youth").parentId)
        assertTrue("showQA" in get().profile("youth").overrides)
    }

    @Test
    fun `a master's card lists its followers, and one click opens a follower`() = profilesTab(doc()) { _ ->
        onNodeWithText("Linked profiles").assertExists()
        onAllNodes(hasTextExactly("Youth night") and hasAnyAncestor(hasTestTag(CONTEXT_CARD_TAG)), useUnmergedTree = true)[0]
            .performClick()
        waitForIdle()
        assertShown("Linked to Sanctuary")
    }

    @Test
    fun `a standalone profile links keeping its own values, or matching the master's`() = profilesTab(doc()) { get ->
        select("stream")
        onNodeWithTag(MASTER_PICKER_TAG).assertExists()
        actionKey("Link and keep my values")
        assertEquals("main", get().profile("stream").parentId)
        assertTrue("showQA" in get().profile("stream").overrides)
        assertFalse(get().profile("stream").showQA)
        actionKey("Unlink")
        assertNull(get().profile("stream").parentId)
        actionKey("Link and match Sanctuary")
        assertTrue(get().profile("stream").overrides.isEmpty())
        assertTrue(get().profile("stream").showQA)
    }

    @Test
    fun `General makes a linked profile, locks a follower's mode and keeps a master from deletion`() =
        profilesTab(doc()) { get ->
            onNodeWithText("Unlink or delete its linked profiles before deleting it.").assertExists()
            onNodeWithTag(actionKeyTag("Delete")).assertIsNotEnabled()
            actionKey("Create linked profile")
            val made = get().projectionSettings.outputProfiles.filter { it.parentId == "main" }
            assertEquals(3, made.size)
            assertShown("Linked to Sanctuary")
            onNodeWithText("Set by Sanctuary. Unlink to change it.").assertExists()
            segment("Lower third").performClick()
            waitForIdle()
            assertEquals(Constants.DISPLAY_MODE_FULLSCREEN, made.last().displayMode)
            assertEquals(Constants.DISPLAY_MODE_FULLSCREEN,
                    get().projectionSettings.outputProfiles.last { it.parentId == "main" }.displayMode)
        }
}
