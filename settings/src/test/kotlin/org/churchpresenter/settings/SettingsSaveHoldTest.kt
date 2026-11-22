package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsSaveHoldTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-settings-hold").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun onDisk(): AppSettings = SettingsManager(dir).loadSettings()

    @Test
    fun `a held save is on disk when it returns`() {
        SettingsManager(dir).saveSettingsAndHold(AppSettings(songFavoritesPanelHeightDp = 444))

        assertEquals(444, onDisk().songFavoritesPanelHeightDp)
    }

    @Test
    fun `no save after a held one replaces it, from that manager or another`() {
        val window = SettingsManager(dir)
        window.saveSettings(AppSettings(songFavoritesPanelHeightDp = 200))
        SettingsManager(dir).saveSettingsAndHold(AppSettings(songFavoritesPanelHeightDp = 444))

        window.saveSettings(AppSettings(songFavoritesPanelHeightDp = 200))
        SettingsManager(dir).saveSettings(AppSettings(songFavoritesPanelHeightDp = 300))
        SettingsManager.flushAllSaves()

        assertEquals(444, onDisk().songFavoritesPanelHeightDp)
    }

    @Test
    fun `a held save that cannot be written throws and holds nothing`() {
        // A folder where the temp file goes: the write fails before anything is replaced.
        val blocker = File(dir, "settings.json.tmp").apply { mkdirs() }
        val manager = SettingsManager(dir)

        assertFailsWith<Exception> { manager.saveSettingsAndHold(AppSettings(songFavoritesPanelHeightDp = 444)) }

        blocker.delete()
        manager.saveSettings(AppSettings(songFavoritesPanelHeightDp = 300))
        manager.flushSettings()
        assertEquals(300, onDisk().songFavoritesPanelHeightDp)
    }

    @Test
    fun `a held save leaves other settings files free to save`() {
        val other = File(dir, "other").apply { mkdirs() }
        SettingsManager(dir).saveSettingsAndHold(AppSettings(songFavoritesPanelHeightDp = 444))

        SettingsManager(other).apply {
            saveSettings(AppSettings(songFavoritesPanelHeightDp = 300))
            flushSettings()
        }

        assertEquals(300, SettingsManager(other).loadSettings().songFavoritesPanelHeightDp)
    }
}
