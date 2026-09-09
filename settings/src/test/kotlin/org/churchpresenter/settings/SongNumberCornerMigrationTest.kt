package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The song number gaining a corner, defaulting to the bottom right it was already drawn in.
 *
 * A corner overrides `songNumberPosition` and `songNumberHorizontalAlignment` outright, so shipping
 * the new default into existing files would move the number for every church that had placed it
 * anywhere else -- with the controls they placed it with still reading what they had chosen. The
 * migration pins those documents to [Constants.NONE]; only a fresh install gets the corner.
 */
class SongNumberCornerMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-song-number-corner-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    @Test
    fun `a fresh install is cornered bottom right`() {
        assertEquals(Constants.BOTTOM_RIGHT, SongSettings().songNumberCorner)
        assertEquals(Constants.BOTTOM_RIGHT, SongSettings().songNumberLowerThirdCorner)
    }

    @Test
    fun `an existing document keeps the placement it configured`() {
        val settings = decode(
            """
            {"settingsVersion":8,
             "songSettings":{"songNumberPosition":"AboveVerse","songNumberHorizontalAlignment":"Left"}}
            """.trimIndent(),
        )

        assertEquals(Constants.NONE, settings.songSettings.songNumberCorner)
        assertEquals(Constants.NONE, settings.songSettings.songNumberLowerThirdCorner)
        assertEquals(Constants.ABOVE_VERSE, settings.songSettings.songNumberPosition)
        assertEquals(Constants.LEFT, settings.songSettings.songNumberHorizontalAlignment)
    }

    @Test
    fun `an existing document that never touched the number is pinned too`() {
        // Its number was in the bottom right either way, so the corner would look the same -- but
        // guessing which of them customized it is not the migration's job.
        val settings = decode("""{"settingsVersion":8,"songSettings":{"marginTop":12}}""")

        assertEquals(Constants.NONE, settings.songSettings.songNumberCorner)
        assertEquals(12, settings.songSettings.marginTop, "and the rest of the section survives")
    }

    @Test
    fun `a document with no song settings at all is left alone`() {
        val settings = decode("""{"settingsVersion":8}""")

        assertEquals(Constants.BOTTOM_RIGHT, settings.songSettings.songNumberCorner)
    }

    @Test
    fun `a corner already chosen is not overwritten`() {
        // Written by a newer build, opened by an older one and rolled forward again.
        val settings = decode(
            """
            {"settingsVersion":8,
             "songSettings":{"songNumberCorner":"Top Left","songNumberLowerThirdCorner":"Top Right"}}
            """.trimIndent(),
        )

        assertEquals(Constants.TOP_LEFT, settings.songSettings.songNumberCorner)
        assertEquals(Constants.TOP_RIGHT, settings.songSettings.songNumberLowerThirdCorner)
    }

    @Test
    fun `a current-version document is left exactly as it is`() {
        val settings = decode(
            """
            {"settingsVersion":${AppSettings.CURRENT_SETTINGS_VERSION},
             "songSettings":{"songNumberPosition":"AboveVerse"}}
            """.trimIndent(),
        )

        assertEquals(Constants.BOTTOM_RIGHT, settings.songSettings.songNumberCorner)
    }

    @Test
    fun `replaying the migration changes nothing the second time`() {
        val once = decode("""{"settingsVersion":8,"songSettings":{"songNumberPosition":"AboveVerse"}}""")
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val twice = decode(json.encodeToString(AppSettings.serializer(), once))

        assertEquals(once.songSettings.songNumberCorner, twice.songSettings.songNumberCorner)
        assertEquals(
            once.songSettings.songNumberLowerThirdCorner,
            twice.songSettings.songNumberLowerThirdCorner,
        )
    }
}
