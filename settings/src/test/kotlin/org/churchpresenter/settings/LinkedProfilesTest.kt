package org.churchpresenter.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Linked profiles: a master, and profiles that keep only what they changed.
 *
 * Every profile stores every value, so the tests read the values an output would draw with -- the
 * follower's own fields -- rather than any separate record of what it inherits.
 */
class LinkedProfilesTest {

    private val kjv = BibleTranslationSettings(fileName = "kjv.spb")
    private val rst = BibleTranslationSettings(fileName = "rst.spb")

    private fun master(fontSize: Int = 70, fadeIn: Boolean = true) = OutputProfile(
        id = "main",
        name = "Sanctuary",
        bibleSettings = BibleSettings(
            translations = listOf(kjv.copy(textFontSize = fontSize), rst.copy(textFontSize = fontSize)),
            fadeIn = fadeIn,
        ),
    )

    private fun follower(overrides: Set<String> = emptySet(), fontSize: Int = 70,
            showQa: Boolean = true) = OutputProfile(
        id = "youth",
        name = "Youth night",
        parentId = "main",
        showQA = showQa,
        overrides = overrides,
        bibleSettings = BibleSettings(
            translations = listOf(kjv.copy(textFontSize = fontSize), rst.copy(textFontSize = 70)),
        ),
    )

    private fun proj(vararg profiles: OutputProfile) = ProjectionSettings(outputProfiles = profiles.toList())

    private fun ProjectionSettings.p(id: String) = outputProfiles.first { it.id == id }

    private fun ProjectionSettings.order() = outputProfiles.map { it.id }

    // ── Paths ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a translation is addressed by its file name and identity is never a path`() {
        val paths = master().settingPaths()
        assertTrue("bibleSettings.translations[kjv.spb].textFontSize" in paths)
        assertFalse(paths.keys.any { it == "id" || it == "name" || it == "previewWidth" })
        // The library folder is the document's, never the profile's.
        assertFalse(paths.keys.any { it == "bibleSettings.storageDirectory" })
    }

    @Test
    fun `a value can be written back at its path and a path that is not there changes nothing`() {
        val m = master()
        val written = m.withValueAt("bibleSettings.translations[rst.spb].textFontSize", JsonPrimitive(42))
        assertEquals(42, written.bibleSettings.translations[1].textFontSize)
        assertEquals(70, written.bibleSettings.translations[0].textFontSize)
        assertEquals(m, m.withValueAt("bibleSettings.translations[nope.spb].textFontSize", JsonPrimitive(1)))
        assertEquals(m, m.withValueAt("bibleSettings.fadeIn.inner", JsonPrimitive(1)))
    }

    @Test
    fun `pathWithin matches the path itself, its fields and its entries`() {
        assertTrue(pathWithin("bibleSettings", "bibleSettings"))
        assertTrue(pathWithin("bibleSettings.fadeIn", "bibleSettings"))
        assertTrue(pathWithin("bibleSettings.translations[kjv.spb]", "bibleSettings.translations"))
        assertFalse(pathWithin("bibleSettingsX", "bibleSettings"))
    }

    @Test
    fun `changed paths ignore a background surface the profile only follows`() {
        val a = OutputProfile(id = "a")
        val b = a.copy(backgroundSettings = a.backgroundSettings.copy(defaultBackgroundColor = "#123456"))
        assertTrue(changedPaths(a, b).isEmpty())
        val owned = b.copy(backgroundOverrides = setOf(BackgroundSurface.DEFAULT.name))
        assertTrue("backgroundSettings.defaultBackgroundColor" in changedPaths(a, owned))
    }

    @Test
    fun `plain text reads switches, numbers, text and lists`() {
        assertEquals("On", JsonPrimitive(true).plainText())
        assertEquals("Nope", JsonPrimitive(false).plainText(off = "Nope"))
        assertEquals("70", JsonPrimitive(70.0).plainText())
        assertEquals("Arial", JsonPrimitive("Arial").plainText())
        assertEquals("1, On", JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(true))).plainText())
        assertEquals("", JsonObject(emptyMap()).plainText())
    }

    // ── Resolving on load ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a follower takes its master's values except the ones it changed`() {
        val resolved = proj(master(fontSize = 90, fadeIn = false), follower(setOf("showQA"), showQa = false))
            .withLinksResolved()
        val youth = resolved.p("youth")
        assertEquals(90, youth.bibleSettings.translations[0].textFontSize)
        assertFalse(youth.bibleSettings.fadeIn)
        assertFalse(youth.showQA)
        assertEquals("Youth night", youth.name)
    }

    @Test
    fun `a link to a missing or linked master is cleared and the values are kept`() {
        val orphan = follower(setOf("showQA"), showQa = false).copy(parentId = "gone")
        val chained = OutputProfile(id = "c", parentId = "youth", showPictures = false,
                overrides = setOf("showPictures"))
        val resolved = proj(master(), follower(), orphan.copy(id = "o"), chained).withLinksResolved()
        assertNull(resolved.p("o").parentId)
        assertTrue(resolved.p("o").overrides.isEmpty())
        assertFalse(resolved.p("o").showQA)
        assertNull(resolved.p("c").parentId)
        assertFalse(resolved.p("c").showPictures)
    }

    @Test
    fun `the list is put in blocks, each master followed by its linked profiles`() {
        val other = OutputProfile(id = "other")
        val resolved = proj(follower(), other, master()).withLinksResolved()
        assertEquals(listOf("other", "main", "youth"), resolved.order())
    }

    @Test
    fun `a new key of a map the master left empty reaches the follower`() {
        val placed = follower().copy(
            lowerThirdPlacements = mapOf(PlaceableContent.entries.first() to LowerThirdPlacement.entries.last()),
            overrides = setOf("lowerThirdPlacements.${PlaceableContent.entries.first().name}"),
        )
        val resolved = proj(master(), placed).withLinksResolved()
        assertEquals(LowerThirdPlacement.entries.last(), resolved.p("youth").lowerThirdPlacements.values.single())
    }

    // ── Editing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an edit on a follower becomes its own and its display mode stays the master's`() {
        val edited = proj(master(), follower()).withLinksResolved().editProfile("youth") {
            it.copy(showPictures = false, displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)
        }
        val youth = edited.p("youth")
        assertEquals(setOf("showPictures"), youth.overrides)
        assertFalse(youth.showPictures)
        assertEquals(master().displayMode, youth.displayMode)
    }

    @Test
    fun `an edit on a master reaches every follower that has not changed that value`() {
        val start = proj(master(), follower(setOf("bibleSettings.fadeIn"))).withLinksResolved()
        val edited = start.editProfile("main") { m ->
            m.copy(showMedia = false, bibleSettings = m.bibleSettings.copy(fadeIn = false))
        }
        assertFalse(edited.p("youth").showMedia)
        assertTrue(edited.p("youth").bibleSettings.fadeIn, "the follower's own value stays")
    }

    @Test
    fun `editing a profile that is not there changes nothing`() {
        val start = proj(master())
        assertSame(start, start.editProfile("nope") { it.copy(showMedia = false) })
    }

    @Test
    fun `revert gives the values under a prefix back to the master`() {
        val start = proj(master(fontSize = 90), follower(setOf("showQA",
                "bibleSettings.translations[kjv.spb].textFontSize"), 50, false))
            .withLinksResolved()
        assertEquals(50, start.p("youth").bibleSettings.translations[0].textFontSize)
        val reverted = start.revertToMaster("youth", listOf("bibleSettings"))
        assertEquals(90, reverted.p("youth").bibleSettings.translations[0].textFontSize)
        assertEquals(setOf("showQA"), reverted.p("youth").overrides)
        assertFalse(reverted.p("youth").showQA)
    }

    @Test
    fun `unlink keeps every value and places the profile after its old master's block`() {
        val other = OutputProfile(id = "other")
        val second = follower().copy(id = "easter", name = "Easter")
        val start = proj(master(fontSize = 90), follower(setOf("showQA"), showQa = false), second,
                other).withLinksResolved()
        val unlinked = start.unlinkProfile("youth")
        val youth = unlinked.p("youth")
        assertNull(youth.parentId)
        assertTrue(youth.overrides.isEmpty())
        assertEquals(90, youth.bibleSettings.translations[0].textFontSize)
        assertFalse(youth.showQA)
        assertEquals(listOf("main", "easter", "youth", "other"), unlinked.order())
        // A profile that follows nothing, or is not there, is left alone.
        assertSame(unlinked, unlinked.unlinkProfile("youth"))
        assertSame(unlinked, unlinked.unlinkProfile("nope"))
    }

    @Test
    fun `link and keep holds the differences as overrides, link and match drops them`() {
        val standalone = OutputProfile(id = "s", showQA = false, displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)
        val start = proj(master(), standalone)
        val kept = start.linkProfile("s", "main", keepOwnValues = true)
        assertTrue("showQA" in kept.p("s").overrides)
        assertFalse("displayMode" in kept.p("s").overrides)
        assertFalse(kept.p("s").showQA)
        assertEquals(master().displayMode, kept.p("s").displayMode)
        val matched = start.linkProfile("s", "main", keepOwnValues = false)
        assertTrue(matched.p("s").overrides.isEmpty())
        assertTrue(matched.p("s").showQA)
    }

    @Test
    fun `linking is one level only`() {
        val start = proj(master(), follower(), OutputProfile(id = "s")).withLinksResolved()
        assertSame(start, start.linkProfile("s", "youth", true), "a follower cannot be a master")
        assertSame(start, start.linkProfile("main", "s", true), "a master with followers cannot follow")
        assertSame(start, start.linkProfile("s", "s", true))
        assertSame(start, start.linkProfile("nope", "main", true))
        assertSame(start, start.linkProfile("s", "nope", true))
    }

    @Test
    fun `a new linked profile joins the end of its master's block with the master's shape`() {
        val wide = master().copy(previewWidth = 1080, previewHeight = 1920)
        val start = proj(wide, follower(), OutputProfile(id = "other"))
        val created = start.createLinkedProfile("main", OutputProfile(id = "new", name = "New"))
        assertEquals(listOf("main", "youth", "new", "other"), created.order())
        assertEquals("main", created.p("new").parentId)
        assertEquals(1080, created.p("new").previewWidth)
        assertSame(start, start.createLinkedProfile("youth", OutputProfile(id = "x")))
        assertSame(start, start.createLinkedProfile("nope", OutputProfile(id = "x")))
    }

    @Test
    fun `counting changes, and reading who follows whom`() {
        val start = proj(master(), follower(setOf("showQA", "bibleSettings.fadeIn")))
        assertEquals(2, overrideCount(start.p("youth")))
        assertEquals(1, overrideCount(start.p("youth"), listOf("bibleSettings")))
        assertEquals(listOf("youth"), start.linkedTo("main").map { it.id })
        assertEquals("main", start.masterOf(start.p("youth"))?.id)
        assertNull(start.masterOf(start.p("main")))
    }

    @Test
    fun `a master with followers cannot be deleted and a duplicate follows nothing`() {
        val start = proj(master(), follower(setOf("showQA"), showQa = false)).withLinksResolved()
        assertSame(start, start.deleteOutputProfile("main"))
        val duplicated = start.duplicateOutputProfile("youth", "Youth copy")
        val copy = duplicated.outputProfiles.last()
        assertNull(copy.parentId)
        assertTrue(copy.overrides.isEmpty())
        assertFalse(copy.showQA)
    }
}
