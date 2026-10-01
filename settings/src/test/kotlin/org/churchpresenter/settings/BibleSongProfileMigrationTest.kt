package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Version 22: the document keeps only the install-wide part of the Bible and Song settings, every
 * profile is given what it lacked first, and the main window follows a profile for the rest.
 */
class BibleSongProfileMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-bible-song-profile-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    private fun root(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    /**
     * A version-21 document: its own Song and Bible looks, a profile `a` carrying its own of both --
     * but styling only one of the two translations -- and a profile `b` carrying neither.
     */
    private val v21 = """
        {"settingsVersion":21,
         "songSettings":{"storageDirectory":"/songs","marginTop":12,"autoRepeatChorus":false,"titleSlideEnabled":false},
         "bibleSettings":{"storageDirectory":"/bibles","splitLongVerses":false,"translations":[
            {"fileName":"kjv.spb","customName":"King James","textColor":"#FF0000"},
            {"fileName":"rst.spb","textColor":"#0000FF"}]},
         "projectionSettings":{
           "screenAssignments":[{"activeProfileId":"a"}],
           "outputProfiles":[
            {"id":"a","songSettings":{"marginTop":40},
             "bibleSettings":{"translations":[{"fileName":"kjv.spb","textColor":"#00FF00"}]}},
            {"id":"b"}
         ]}}
    """.trimIndent()

    private fun AppSettings.profile(id: String) = projectionSettings.outputProfiles.first { it.id == id }

    @Test
    fun `a profile keeps its own look and is given the translation it never styled`() {
        val a = decode(v21).profile("a")

        assertEquals(40, a.songSettings.marginTop)
        val colors = a.bibleSettings.translations.associate { it.fileName to it.textColor }
        assertEquals(mapOf("kjv.spb" to "#00FF00", "rst.spb" to "#0000FF"), colors)
    }

    @Test
    fun `a profile with no Song or Bible settings of its own is given the document's`() {
        val b = decode(v21).profile("b")

        assertEquals(12, b.songSettings.marginTop)
        assertFalse(b.songSettings.autoRepeatChorus)
        assertFalse(b.bibleSettings.splitLongVerses)
        assertEquals("#FF0000", b.bibleSettings.translations.first().textColor)
    }

    @Test
    fun `every output draws what it drew before`() {
        val settings = decode(v21)
        val a = settings.resolvedFor(settings.profile("a")).bibleSettings.translations

        assertEquals(listOf("kjv.spb", "rst.spb"), a.map { it.fileName }, "the document's stack and order")
        assertEquals(listOf("#00FF00", "#0000FF"), a.map { it.textColor })
        assertEquals("King James", a.first().customName, "the document's name for it")
        assertEquals("/songs", settings.resolvedFor(settings.profile("a")).songSettings.storageDirectory)
    }

    @Test
    fun `the document keeps only what is one per install`() {
        val settings = decode(v21)

        assertEquals("/songs", settings.songSettings.storageDirectory)
        assertEquals("/bibles", settings.bibleSettings.storageDirectory)
        assertEquals(listOf("King James", ""), settings.bibleSettings.translations.map { it.customName })
        assertEquals(SongSettings().marginTop, settings.songSettings.marginTop, "its own look is gone")
        assertEquals(BibleTranslationSettings().textColor, settings.bibleSettings.translations.first().textColor)
    }

    @Test
    fun `a save writes only the install-wide keys`() {
        val manager = SettingsManager()
        manager.saveSettings(manager.migrateAndDecode(v21))

        val saved = root(File(home, ".churchpresenter/settings.json").readText())
        val song = saved.getValue("songSettings").jsonObject
        val bible = saved.getValue("bibleSettings").jsonObject
        assertTrue(SONG_GLOBAL_KEYS.containsAll(song.keys), "song keys: ${song.keys - SONG_GLOBAL_KEYS}")
        assertTrue((BIBLE_GLOBAL_KEYS + BIBLE_STACK_KEY).containsAll(bible.keys))
        bible.getValue(BIBLE_STACK_KEY).jsonArray.forEach { entry ->
            assertTrue(BIBLE_TRANSLATION_GLOBAL_KEYS.containsAll(entry.jsonObject.keys))
        }
    }

    @Test
    fun `a document with no profiles makes its factory profile from its own look`() {
        val settings = decode("""{"settingsVersion":21,"songSettings":{"marginTop":12}}""")
        val factory = settings.projectionSettings.outputProfiles.single()

        assertEquals(DEFAULT_OUTPUT_PROFILE_ID, factory.id)
        assertEquals(12, factory.songSettings.marginTop)
    }

    // ── The profile the main window follows ─────────────────────────────────────────────────────

    private fun projection(profiles: List<String>, vararg assigned: String?) = ProjectionSettings(
        outputProfiles = profiles.map { OutputProfile(id = it) },
        screenAssignments = assigned.map { ScreenAssignment(activeProfileId = it) },
    )

    @Test
    fun `the main window follows the first profile an output is using`() {
        assertEquals("b", projection(listOf("a", "b"), "b", "a").operatorProfile()?.id)
    }

    @Test
    fun `an output's network sends count as using a profile too`() {
        val proj = projection(listOf("a", "b")).copy(ndiOutputs = listOf(ScreenAssignment(activeProfileId = "b")))
        assertEquals("b", proj.operatorProfile()?.id)
    }

    @Test
    fun `with no output on a profile it follows the first one, and with none at all nothing`() {
        assertEquals("a", projection(listOf("a", "b"), BLANK_OUTPUT_PROFILE_ID, "gone").operatorProfile()?.id)
        assertNull(projection(emptyList()).operatorProfile())
    }

    @Test
    fun `the main window's song and Bible settings are the followed profile's`() {
        val settings = decode(v21)

        assertEquals(40, settings.operatorSongSettings().marginTop)
        assertEquals("#00FF00", settings.operatorBibleSettings().translations.first().textColor)
        assertEquals("/songs", settings.operatorSongSettings().storageDirectory, "with the install's own keys")
    }

    @Test
    fun `with no profiles at all the document's own settings are used`() {
        val settings = AppSettings(
            songSettings = SongSettings(marginTop = 7),
            bibleSettings = BibleSettings(splitLongVerses = false),
            projectionSettings = projection(emptyList()),
        )

        assertEquals(7, settings.operatorSongSettings().marginTop)
        assertFalse(settings.operatorBibleSettings().splitLongVerses)
    }

    @Test
    fun `the answer is remembered for the same settings and redone for new ones`() {
        val settings = decode(v21)
        assertSame(settings.operatorSongSettings(), settings.operatorSongSettings())
        assertSame(settings.operatorBibleSettings(), settings.operatorBibleSettings())

        val unassigned = settings.projectionSettings.copy(screenAssignments = emptyList())
        val moved = settings.copy(projectionSettings = unassigned)
        assertEquals(40, moved.operatorSongSettings().marginTop, "still a, the first profile")
    }
}
