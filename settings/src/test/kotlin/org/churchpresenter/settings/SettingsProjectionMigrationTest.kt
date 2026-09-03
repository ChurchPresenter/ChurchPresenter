package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsProjectionMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-settings-projection-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun assignments(projectionJson: String): List<ScreenAssignment> =
        SettingsManager()
            .migrateAndDecode("""{"projectionSettings":$projectionJson}""")
            .projectionSettings
            .screenAssignments

    private fun withAssignments(vararg assignmentJson: String): List<ScreenAssignment> =
        assignments("""{"screenAssignments":[${assignmentJson.joinToString(",")}]}""")

    @Test
    fun `a screen with scripture switched off becomes bible mode off`() {
        val assignment = withAssignments("""{"showBible":false}""").single()

        assertEquals(Constants.SONG_LANG_OFF, assignment.bibleMode)
        assertFalse(assignment.showBible)
    }

    @Test
    fun `a screen with songs switched off becomes song mode off`() {
        val assignment = withAssignments("""{"showSongs":false}""").single()

        assertEquals(Constants.SONG_LANG_OFF, assignment.songMode)
        assertFalse(assignment.showSongs)
    }

    @Test
    fun `a screen with both switched off gets both modes off`() {
        val assignment = withAssignments("""{"showBible":false,"showSongs":false}""").single()

        assertEquals(Constants.SONG_LANG_OFF, assignment.bibleMode)
        assertEquals(Constants.SONG_LANG_OFF, assignment.songMode)
    }

    @Test
    fun `switching scripture off leaves songs on`() {
        val assignment = withAssignments("""{"showBible":false}""").single()

        assertTrue(assignment.showSongs, "only the flag that was set may be migrated")
    }

    @Test
    fun `a screen that had both switched on is left at its defaults`() {
        val assignment = withAssignments("""{"showBible":true,"showSongs":true}""").single()

        assertTrue(assignment.showBible)
        assertTrue(assignment.showSongs)
    }

    @Test
    fun `a mode already stored alongside the old flag wins`() {
        val assignment = withAssignments("""{"showBible":false,"bibleMode":"secondary"}""").single()

        assertTrue(assignment.showBible, "the stored mode must not be overwritten by the old flag")
        assertEquals(listOf(1), assignment.bibleTranslations, "'secondary' is now the second translation")
    }

    @Test
    fun `only the screen that needs migrating is rewritten`() {
        val migrated = withAssignments("""{"showBible":false}""", """{"showSongs":false}""", "{}")

        assertEquals(3, migrated.size)
        assertEquals(Constants.SONG_LANG_OFF, migrated[0].bibleMode)
        assertTrue(migrated[0].showSongs)
        assertEquals(Constants.SONG_LANG_OFF, migrated[1].songMode)
        assertTrue(migrated[1].showBible)
        assertTrue(migrated[2].showBible)
        assertTrue(migrated[2].showSongs)
    }

    @Test
    fun `the old flags do not survive as their own screen setting`() {
        val assignment = withAssignments("""{"showBible":false,"targetDisplay":2}""").single()

        assertEquals(Constants.SONG_LANG_OFF, assignment.bibleMode)
        assertEquals(2, assignment.targetDisplay)
    }

    @Test
    fun `the old primary-only mode becomes the first translation`() {
        val assignment = withAssignments("""{"bibleMode":"primary"}""").single()

        assertTrue(assignment.showBible)
        assertEquals(listOf(0), assignment.bibleTranslations)
    }

    @Test
    fun `a screen already on the current mode keeps showing every translation`() {
        val assignment = withAssignments("""{"bibleMode":"both"}""").single()

        assertTrue(assignment.showBible)
        assertEquals(emptyList(), assignment.bibleTranslations, "empty means all of them, now and later")
    }

    @Test
    fun `running the migration a second time changes nothing`() {
        val once = SettingsManager().migrateAndDecode(
            """{"projectionSettings":{"screenAssignments":[{"showBible":false}]}}""",
        )
        val twice = SettingsManager().migrateAndDecode(
            """{"projectionSettings":{"screenAssignments":[{"showBible":false}]}}""",
        )

        assertEquals(once.projectionSettings.screenAssignments, twice.projectionSettings.screenAssignments)
    }

    @Test
    fun `the numbered screen fields become a list in order`() {
        val migrated = assignments(
            """{"numberOfWindows":2,"screen1Assignment":{"targetDisplay":0},"screen2Assignment":{"targetDisplay":1}}""",
        )

        assertEquals(listOf(0, 1), migrated.map { it.targetDisplay })
    }

    @Test
    fun `all four numbered screens are carried across`() {
        val migrated = assignments(
            """{"screen1Assignment":{"targetDisplay":0},"screen2Assignment":{"targetDisplay":1},""" +
                """"screen3Assignment":{"targetDisplay":2},"screen4Assignment":{"targetDisplay":3}}""",
        )

        assertEquals(listOf(0, 1, 2, 3), migrated.map { it.targetDisplay })
    }

    @Test
    fun `a gap in the numbered screens does not leave a hole in the list`() {
        val migrated = assignments(
            """{"screen1Assignment":{"targetDisplay":0},"screen4Assignment":{"targetDisplay":3}}""",
        )

        assertEquals(listOf(0, 3), migrated.map { it.targetDisplay })
    }

    @Test
    fun `a file already carrying a list is left alone`() {
        val migrated = assignments(
            """{"screenAssignments":[{"targetDisplay":7}],"screen1Assignment":{"targetDisplay":0}}""",
        )

        assertEquals(listOf(7), migrated.map { it.targetDisplay })
    }

    @Test
    fun `the numbered screens are still converted when one carries an old flag`() {
        val migrated = assignments(
            """{"screen1Assignment":{"targetDisplay":0,"showBible":false},"screen2Assignment":{"targetDisplay":1}}""",
        )

        assertEquals(listOf(0, 1), migrated.map { it.targetDisplay })
    }

    @Test
    fun `a settings file with no projection block at all still decodes`() {
        val settings = SettingsManager().migrateAndDecode("""{"showBible":false}""")

        assertTrue(settings.projectionSettings.screenAssignments.isNotEmpty())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `migrating the screens keeps the rest of the projection block`() {
        val decoded = SettingsManager().migrateAndDecode(
            """{"projectionSettings":{"vlcPath":"/opt/vlc","screenAssignments":[{"showBible":false}],"audioOutputDeviceId":"HDMI"}}"""
        ).projectionSettings

        assertEquals(Constants.SONG_LANG_OFF, decoded.screenAssignments.single().bibleMode)
        assertEquals("/opt/vlc", decoded.vlcPath)
        assertEquals("HDMI", decoded.audioOutputDeviceId)
    }

    @Test
    fun `a settings file written before ffmpeg could be overridden still decodes`() {
        val decoded = SettingsManager().migrateAndDecode(
            """{"projectionSettings":{"vlcPath":"/opt/vlc","ndiRuntimePath":"/opt/ndi"}}"""
        ).projectionSettings

        assertEquals("", decoded.ffmpegPath, "no entry means the bundled ffmpeg, not a broken path")
        assertEquals("/opt/vlc", decoded.vlcPath)
        assertEquals("/opt/ndi", decoded.ndiRuntimePath)
    }

    @Test
    fun `migrating the screens keeps the rest of the settings file`() {
        val decoded = SettingsManager().migrateAndDecode(
            """{"language":"ru","projectionSettings":{"screenAssignments":[{"showSongs":false}]}}"""
        )

        assertEquals(Constants.SONG_LANG_OFF, decoded.projectionSettings.screenAssignments.single().songMode)
        assertEquals("ru", decoded.language)
    }

    @Test
    fun `converting the numbered screens keeps the rest of the projection block`() {
        val decoded = SettingsManager().migrateAndDecode(
            """{"projectionSettings":{"vlcPath":"/opt/vlc","screen1Assignment":{"targetDisplay":1}}}"""
        ).projectionSettings

        assertEquals(1, decoded.screenAssignments.size)
        assertEquals("/opt/vlc", decoded.vlcPath)
    }

    @Test
    fun `converting the numbered screens keeps the rest of the settings file`() {
        val decoded = SettingsManager().migrateAndDecode(
            """{"language":"pl","projectionSettings":{"screen1Assignment":{"targetDisplay":1}}}"""
        )

        assertEquals(1, decoded.projectionSettings.screenAssignments.size)
        assertEquals("pl", decoded.language)
    }
}
