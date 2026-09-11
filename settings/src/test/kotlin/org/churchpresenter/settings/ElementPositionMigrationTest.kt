package org.churchpresenter.settings

import org.churchpresenter.settings.AppSettings.Companion.CURRENT_SETTINGS_VERSION
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The title's and the number's position put back onto the two rows the presenter draws.
 *
 * `titlePosition` was once a vertical alignment and for a while defaulted to `Middle`; a file from
 * then still says so, and a title positioned on neither row is drawn on neither. [migrateElementPositions]
 * reads it the way the old dropdown did: anything but below is above.
 */
class ElementPositionMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-element-position-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    @Test
    fun `a stale Middle becomes above the verse`() {
        val migrated = SongSettings(
            titlePosition = Constants.MIDDLE,
            titleLowerThirdPosition = Constants.MIDDLE,
            songNumberPosition = Constants.TOP,
            songNumberLowerThirdPosition = Constants.BOTTOM,
        ).migrateElementPositions()

        assertEquals(Constants.ABOVE_VERSE, migrated.titlePosition)
        assertEquals(Constants.ABOVE_VERSE, migrated.titleLowerThirdPosition)
        assertEquals(Constants.ABOVE_VERSE, migrated.songNumberPosition)
        assertEquals(Constants.ABOVE_VERSE, migrated.songNumberLowerThirdPosition)
    }

    @Test
    fun `below the verse is kept`() {
        val chosen = SongSettings(titlePosition = Constants.BELOW_VERSE, songNumberPosition = Constants.BELOW_VERSE)

        assertEquals(chosen, chosen.migrateElementPositions())
    }

    @Test
    fun `a fresh install is untouched`() {
        assertEquals(SongSettings(), SongSettings().migrateElementPositions())
    }

    @Test
    fun `a document already at the current version is repaired on load`() {
        // Not behind a version gate: the stale value sits in files the version says are current.
        val settings = SettingsManager().migrateAndDecode(
            """{"settingsVersion":$CURRENT_SETTINGS_VERSION,"songSettings":{"titlePosition":"Middle"}}""",
        )

        assertEquals(Constants.ABOVE_VERSE, settings.songSettings.titlePosition)
    }
}
