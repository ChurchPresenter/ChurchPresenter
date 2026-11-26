package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongSaveRuleTest {

    private val resolver = RuleIntentResolver()

    private fun actionOf(text: String, context: ResolveContext = ResolveContext(language = "en")): HelperAction? =
        (resolver.resolveNow(text, context) as? Resolution.Act)?.action

    private fun french(
        tab: Tabs? = null,
        folderSet: Boolean = true,
        follows: Boolean = false,
    ) = ResolveContext(currentTab = tab, language = "fr", songFolderSet = folderSet, followsAnotherComputer = follows)

    private fun HelperAction?.ringsSave(): Boolean =
        (this as? HelperAction.Highlight)?.tour?.steps?.any { it.target == GuideTargets.SONG_SAVE } == true

    private fun HelperAction?.says(): HelperAction.Say = assertIs<HelperAction.Say>(this)

    @Test
    fun `asked how to save a song, Wick rings the editor's Save`() {
        listOf("how do i save a song", "how do I save the song", "where is save for the song").forEach {
            assertTrue(actionOf(it).ringsSave(), it)
        }
        assertTrue(actionOf("comment enregistrer le chant", french()).ringsSave())
        assertTrue(actionOf("comment enregister le chant", french()).ringsSave(), "as it is often typed")
        assertTrue(actionOf("comment sauvegarder un cantique", french()).ringsSave())
    }

    @Test
    fun `on the Songs tab, saving needs no mention of songs`() {
        assertTrue(actionOf("how do i save", ResolveContext(currentTab = Tabs.SONGS)).ringsSave())
        assertNull(actionOf("how do i save", ResolveContext(currentTab = Tabs.BIBLE)).takeIf { it.ringsSave() })
    }

    @Test
    fun `told saving does not work, Wick says why, most certain reason first`() {
        val follower = actionOf("save song doesn't work", ResolveContext(followsAnotherComputer = true)).says()
        assertNull(follower.offer, "nothing to change here: the songs are the other computer's")

        val noFolder = actionOf("I can't save the song", ResolveContext(songFolderSet = false)).says()
        assertEquals(HelperAction.OpenSettings(SettingsPage.SYSTEM), noFolder.offer)

        val check = actionOf("I click save but nothing happens", ResolveContext(currentTab = Tabs.SONGS)).says()
        assertEquals(HelperAction.OpenSettings(SettingsPage.SYSTEM), check.offer)
        assertEquals(saveTrouble(ResolveContext()), check)
    }

    @Test
    fun `the French operator's words are understood`() {
        val said = actionOf(
            "Je clique sur enregistrer mais ça ne marche pas",
            french(tab = Tabs.SONGS, folderSet = false),
        )
        assertEquals(saveTrouble(ResolveContext(songFolderSet = false)), said)
        assertEquals(
            saveTrouble(ResolveContext()),
            actionOf("le chant ne s'enregistre pas, rien ne se passe", french()),
        )
    }

    @Test
    fun `saving something else is left to its own rules`() {
        listOf("save the schedule", "save as template", "save this song to the schedule").forEach {
            assertTrue(!actionOf(it).ringsSave() && actionOf(it) !is HelperAction.Say, it)
        }
    }
}
