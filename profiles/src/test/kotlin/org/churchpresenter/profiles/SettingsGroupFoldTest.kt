package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.withLinksResolved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Folding the groups of a Profiles page away: the caption folds and opens its group, a folded group
 * says what it holds in one line, Fold all and Open all, the folds kept in the document per page, a
 * search opening what it finds, and a linked profile's own values still marked on a folded caption.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsGroupFoldTest {

    private val biblePage = ProfilePage.Appearance(CustomizePane.BIBLE).navTag()

    private fun AppSettings.folded(page: String = biblePage): Set<String> = profilesFoldedGroups[page].orEmpty()

    /** How many nodes reading [text] are actually drawn -- a folded group's rows are composed, not placed. */
    private fun SkikoComposeUiTest.drawn(text: String): Int {
        val nodes = onAllNodesWithText(text)
        return (0 until nodes.fetchSemanticsNodes().size).count { nodes[it].isDisplayed() }
    }

    private fun SkikoComposeUiTest.fold(key: String) = tap(groupFoldTag(key))

    @Test
    fun `the caption folds its group to a summary and opens it again`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.BIBLE)
        assertTrue(drawn("Font") > 0)
        onNode(hasContentDescription("Fold") and hasAnyAncestor(hasTestTag(groupFoldTag("text"))), true)
            .assertExists()
        fold("text")
        assertEquals(setOf("text"), get().folded())
        assertEquals(0, drawn("Font"))
        // The summary: the font and its size.
        assertTrue(onAllNodesWithText(" pt", substring = true).fetchSemanticsNodes().isNotEmpty())
        onNode(hasContentDescription("Open") and hasAnyAncestor(hasTestTag(groupFoldTag("text"))), true)
            .assertExists()
        fold("text")
        assertTrue(get().folded().isEmpty())
        assertTrue(get().profilesFoldedGroups.isEmpty())
        assertTrue(drawn("Font") > 0)
    }

    @Test
    fun `folds are kept per page, whichever page is open`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.BIBLE)
        fold("position")
        openCustomizePane(CustomizePane.SONGS)
        assertTrue(drawn("Font") > 0)
        fold("slides")
        assertEquals(setOf("position"), get().folded())
        assertEquals(setOf("slides"), get().folded(ProfilePage.Appearance(CustomizePane.SONGS).navTag()))
        openCustomizePane(CustomizePane.BIBLE)
        onNode(hasContentDescription("Open") and hasAnyAncestor(hasTestTag(groupFoldTag("position"))), true)
            .assertExists()
    }

    @Test
    fun `Fold all folds every group on the page and Open all opens them`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.BIBLE)
        tap(linkTag("Fold all"))
        assertTrue(setOf("text", "position", "background").all { it in get().folded() }, "${get().folded()}")
        assertEquals(0, drawn("Font"))
        tap(linkTag("Open all"))
        assertTrue(get().folded().isEmpty())
        assertTrue(drawn("Font") > 0)
    }

    @Test
    fun `a search opens a folded group it finds, and clearing it folds the group again`() =
        profilesTab(profileDocument().copy(profilesFoldedGroups = mapOf(biblePage to setOf("text")))) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            assertEquals(0, drawn("Font"))
            onNodeWithTag(PROFILE_SEARCH_TAG).performTextReplacement("Font")
            waitForIdle()
            assertTrue(drawn("Font") > 0)
            assertEquals(setOf("text"), get().folded())
            onNodeWithTag(PROFILE_SEARCH_TAG).performTextReplacement("")
            waitForIdle()
            assertEquals(0, drawn("Font"))
        }

    @Test
    fun `a group without a caption never folds`() = profilesTab(profileDocument()) { _ ->
        openCustomizePane(CustomizePane.SUBTITLES)
        assertEquals(0, countTag(groupFoldTag("note")))
    }

    @Test
    fun `a folded caption still marks a linked profile's own values`() = profilesTab(linkedDoc()) { _ ->
        tap(profileRowTag("youth"))
        openCustomizePane(CustomizePane.BIBLE)
        fold("text")
        val dotOnCaption = hasTestTag(OVERRIDE_DOT_TAG) and hasAnyAncestor(hasTestTag(groupFoldTag("text")))
        assertEquals(1, onAllNodes(dotOnCaption, useUnmergedTree = true).fetchSemanticsNodes().size)
        // Nothing of the profile's own in Position: no mark there.
        fold("position")
        val dotOnPosition = hasTestTag(OVERRIDE_DOT_TAG) and hasAnyAncestor(hasTestTag(groupFoldTag("position")))
        assertEquals(0, onAllNodes(dotOnPosition, useUnmergedTree = true).fetchSemanticsNodes().size)
        // The folded caption keeps its caption's words.
        onNode(hasTextExactly("TEXT") and hasAnyAncestor(hasTestTag(groupFoldTag("text"))), true).assertExists()
    }

    private fun linkedDoc(): AppSettings {
        val bible = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")))
        val youth = OutputProfile(
            id = "youth", name = "Youth night", parentId = PROFILE_ID,
            bibleSettings = bible.copy(translations = bible.translations.map { it.copy(textFontSize = 50) }),
            overrides = setOf("bibleSettings.translations[kjv.spb].textFontSize"),
        )
        return AppSettings(
            bibleSettings = bible,
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(OutputProfile(id = PROFILE_ID, name = "Main", bibleSettings = bible), youth),
            ).withLinksResolved(),
        )
    }
}
