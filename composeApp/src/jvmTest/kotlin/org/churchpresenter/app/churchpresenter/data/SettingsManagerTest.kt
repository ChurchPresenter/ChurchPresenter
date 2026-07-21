package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Settings load, migration and corruption recovery.
 *
 * This is the highest-stakes file in the app: it holds every screen assignment, background,
 * songbook path and integration setting a church has configured. Losing it silently — through a
 * failed migration, an unreadable file, or a version downgrade stripping fields — means an
 * operator arrives on Sunday to a factory-reset app.
 *
 * `SettingsManager` resolves its paths from `user.home` **at construction**, so each test points
 * that at its own temp directory before building one.
 */
class SettingsManagerTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-settings-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val appDir: File get() = File(home, ".churchpresenter")
    private val settingsFile: File get() = File(appDir, "settings.json")

    private fun writeSettings(json: String) {
        appDir.mkdirs()
        settingsFile.writeText(json)
    }

    private fun backupFiles() = appDir.listFiles()?.filter { it.name.startsWith("settings.json.") }.orEmpty()

    // ── Fresh install ───────────────────────────────────────────────────────────

    @Test
    fun `a fresh install loads defaults stamped at the current version`() {
        val settings = SettingsManager().loadSettings()
        assertEquals(AppSettings.CURRENT_SETTINGS_VERSION, settings.settingsVersion)
        assertTrue(backupFiles().isEmpty(), "nothing to back up on a first run")
    }

    @Test
    fun `settings survive a save and reload`() {
        val manager = SettingsManager()
        manager.saveSettings(manager.loadSettings().copy(theme = "dark", language = "ru", windowWidth = 1600))

        val reloaded = SettingsManager().loadSettings()
        assertEquals("dark", reloaded.theme)
        assertEquals("ru", reloaded.language)
        assertEquals(1600, reloaded.windowWidth)
    }

    // ── Migration from pre-versioning files ─────────────────────────────────────

    @Test
    fun `a pre-versioning file is migrated and backed up`() {
        writeSettings("""{"theme":"dark","language":"ru","windowWidth":1600}""")

        val settings = SettingsManager().loadSettings()

        assertEquals("dark", settings.theme, "user values must survive the migration")
        assertEquals("ru", settings.language)
        assertEquals(1600, settings.windowWidth)
        assertEquals(AppSettings.CURRENT_SETTINGS_VERSION, settings.settingsVersion)

        val backup = assertNotNull(
            backupFiles().firstOrNull { it.name == "settings.json.v0.bak" },
            "a pre-migration snapshot must be kept; found ${backupFiles().map { it.name }}",
        )
        assertTrue("\"theme\":\"dark\"" in backup.readText(), "the backup holds the original bytes")
    }

    @Test
    fun `the legacy screen assignment fields become a screenAssignments list`() {
        writeSettings(
            """{"projectionSettings":{"screen1Assignment":{"targetDisplay":0},
               "screen2Assignment":{"targetDisplay":1},"numberOfWindows":2}}""",
        )
        val settings = SettingsManager().loadSettings()
        assertEquals(2, settings.projectionSettings.screenAssignments.size, "both outputs must be carried over")
    }

    @Test
    fun `legacy showBible and showSongs booleans become off modes`() {
        writeSettings(
            """{"projectionSettings":{"screenAssignments":[
               {"targetDisplay":0,"showBible":false,"showSongs":false},{"targetDisplay":1}]}}""",
        )
        val assignments = SettingsManager().loadSettings().projectionSettings.screenAssignments
        assertEquals("off", assignments[0].bibleMode)
        assertEquals("off", assignments[0].songMode)
        assertEquals("both", assignments[1].bibleMode, "an untouched output keeps the default")
    }

    @Test
    fun `tabs added after a user's last run start hidden`() {
        // No qaSettings/sttSettings key means this user predates those tabs; they must not
        // suddenly appear in the tab bar.
        writeSettings("""{"theme":"dark"}""")
        val hidden = SettingsManager().loadSettings().hiddenTabs
        assertTrue("QA" in hidden)
        assertTrue("STT" in hidden)
    }

    @Test
    fun `a user who had hidden nothing still gets the new tabs hidden`() {
        // hiddenTabs written out as empty by a build that predates both tabs — the migration has to
        // add them rather than leaning on the default set, which this document overrides.
        writeSettings("""{"theme":"dark","hiddenTabs":[]}""")

        val hidden = SettingsManager().loadSettings().hiddenTabs

        assertTrue("QA" in hidden, "a tab nobody has configured must not appear unannounced")
        assertTrue("STT" in hidden)
    }

    @Test
    fun `a user who had hidden other tabs keeps those hidden too`() {
        writeSettings("""{"hiddenTabs":["Web","Canvas"]}""")

        val hidden = SettingsManager().loadSettings().hiddenTabs

        assertTrue("Web" in hidden, "the migration adds to the user's choices rather than replacing them")
        assertTrue("Canvas" in hidden)
        assertTrue("QA" in hidden)
    }

    @Test
    fun `a user who has used the QA tab keeps it visible`() {
        writeSettings("""{"qaSettings":{},"sttSettings":{},"hiddenTabs":[]}""")
        val hidden = SettingsManager().loadSettings().hiddenTabs
        assertFalse("QA" in hidden, "an existing QA user must not have the tab hidden from them")
        assertFalse("STT" in hidden)
    }

    // ── Already current ─────────────────────────────────────────────────────────

    @Test
    fun `a file already at the current version is not rewritten or backed up`() {
        val manager = SettingsManager()
        manager.saveSettings(manager.loadSettings().copy(theme = "dark"))
        val before = settingsFile.readText()

        SettingsManager().loadSettings()

        assertEquals(before, settingsFile.readText(), "loading must not rewrite an up-to-date file")
        assertTrue(backupFiles().isEmpty(), "no migration ran, so no backup should exist")
    }

    // ── Corruption recovery ─────────────────────────────────────────────────────

    @Test
    fun `an unreadable file falls back to defaults and is preserved`() {
        writeSettings("""{"theme":"dark","projectionSet""") // truncated mid-write

        val settings = SettingsManager().loadSettings()
        assertEquals(AppSettings().theme, settings.theme, "defaults are used rather than crashing")

        val preserved = assertNotNull(
            appDir.listFiles()?.firstOrNull { it.name.startsWith("settings.json.corrupt-") },
            "the unreadable original must be kept; found ${appDir.listFiles()?.map { it.name }}",
        )
        assertTrue(
            preserved.readText().startsWith("""{"theme":"dark"""),
            "the preserved copy must hold the original bytes, not the defaults",
        )
    }

    @Test
    fun `the corrupt original is copied, not moved`() {
        writeSettings("""{ not valid json""")
        SettingsManager().loadSettings()
        assertTrue(settingsFile.exists(), "the original must survive until the next save overwrites it")
    }

    // ── Downgrade protection ────────────────────────────────────────────────────

    @Test
    fun `a file from a newer build is backed up before its unknown fields are dropped`() {
        val future = AppSettings.CURRENT_SETTINGS_VERSION + 94
        writeSettings(
            """{"settingsVersion":$future,"theme":"dark","language":"pl",
               "someFutureFeature":{"enabled":true},"futureTopLevelFlag":"important"}""",
        )

        val settings = SettingsManager().loadSettings()

        assertEquals("dark", settings.theme, "known fields still load")
        assertEquals("pl", settings.language)
        assertEquals(future, settings.settingsVersion, "the newer version number must be preserved")

        val backup = assertNotNull(
            backupFiles().firstOrNull { it.name == "settings.json.v$future.bak" },
            "a downgrade must snapshot the full-fidelity original; found ${backupFiles().map { it.name }}",
        )
        val text = backup.readText()
        assertTrue("someFutureFeature" in text, "the backup keeps fields this build cannot represent")
        assertTrue("futureTopLevelFlag" in text)
    }

    @Test
    fun `a newer file keeps its version through a save, so its migrations do not re-run`() {
        val future = AppSettings.CURRENT_SETTINGS_VERSION + 1
        writeSettings("""{"settingsVersion":$future,"theme":"dark"}""")

        val manager = SettingsManager()
        manager.saveSettings(manager.loadSettings())

        assertTrue(
            "\"settingsVersion\":$future" in settingsFile.readText(),
            "downgrading then re-upgrading must not replay migrations over already-migrated data",
        )
    }

    // ── Import path ─────────────────────────────────────────────────────────────

    @Test
    fun `an exported file from an older build is migrated on import`() {
        // Settings → Import decodes an arbitrary file; without migration every converted field
        // would be silently lost.
        val imported = SettingsManager().migrateAndDecode(
            """{"theme":"dark","projectionSettings":{"screen1Assignment":{"targetDisplay":0}}}""",
        )
        assertEquals("dark", imported.theme)
        assertEquals(1, imported.projectionSettings.screenAssignments.size)
        assertEquals(AppSettings.CURRENT_SETTINGS_VERSION, imported.settingsVersion)
    }

    @Test
    fun `importing never writes a backup next to the source file`() {
        SettingsManager().migrateAndDecode("""{"theme":"dark"}""")
        assertTrue(backupFiles().isEmpty(), "import passes no backup target; the user's file is not ours to touch")
    }

    // ── Backward compatibility ──────────────────────────────────────────────────

    @Test
    fun `re-running the whole migration chain is a no-op`() {
        // An older build that ignores settingsVersion re-runs every migration and strips the
        // version field on save. The next load must then produce an identical document.
        val manager = SettingsManager()
        manager.saveSettings(manager.loadSettings().copy(theme = "dark", language = "ru"))
        val current = settingsFile.readText()

        val withoutVersion = current.replace(Regex(""""settingsVersion":\d+,?"""), "")
        val remigrated = SettingsManager().migrateAndDecode(withoutVersion)
        val fresh = SettingsManager().migrateAndDecode(current)

        assertEquals(fresh, remigrated, "the guarded migrations must be idempotent")
    }
}
