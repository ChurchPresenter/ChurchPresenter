package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Version 25: the Signage tab starts hidden, on an update as on a fresh install, as STT does.
 *
 * A file written before it carries an explicit `hiddenTabs` that cannot name it, so the new default
 * alone cannot reach it. The step adds and never replaces, and does not run again on a current file:
 * an operator who showed the tab has a list without it that is a choice.
 */
class SignageTabMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-signage-tab-migration-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    private fun documentWith(version: Int, hidden: String) = """
        {
          "settingsVersion": $version,
          "hiddenTabs": $hidden,
          "qaSettings": {},
          "sttSettings": {}
        }
    """.trimIndent()

    @Test
    fun `a schema 24 file has the Signage tab hidden beside what was hidden already`() {
        val settings = decode(documentWith(version = 24, hidden = """["QA","WEB"]"""))
        assertEquals(setOf("QA", "WEB", SIGNAGE), settings.hiddenTabs)
    }

    @Test
    fun `a current file with the tab shown is left alone`() {
        val settings = decode(documentWith(version = AppSettings.CURRENT_SETTINGS_VERSION, hidden = "[]"))
        assertFalse(SIGNAGE in settings.hiddenTabs)
    }

    @Test
    fun `a fresh install starts with the tab hidden`() {
        assertTrue(SIGNAGE in AppSettings().hiddenTabs)
    }

    private companion object {
        const val SIGNAGE = "SIGNAGE"
    }
}
