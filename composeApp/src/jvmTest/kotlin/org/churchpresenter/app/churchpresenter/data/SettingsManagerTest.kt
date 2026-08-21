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

    @Test
    fun `a second load on the same manager reuses the cached instance rather than rereading the file`() {
        val manager = SettingsManager()
        val first = manager.loadSettings()
        // Written directly, bypassing saveSettings — a real second read would pick this up.
        writeSettings("""{"theme":"mutated-on-disk-behind-the-manager's-back"}""")

        val second = manager.loadSettings()

        assertEquals(first, second, "the cached instance must be returned rather than rereading a changed file")
        assertEquals(AppSettings().theme, second.theme, "the on-disk mutation must not have been read at all")
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

    // ── Reading a damaged version number ────────────────────────────────────────
    //
    // readSettingsVersion falls back to 0 (pre-versioning) whenever the field can't be read as a
    // plain number — but that fallback only decides which migration steps run. The document's own
    // settingsVersion field is still whatever it was, and since AppSettings declares that field as
    // an Int, decoding it back afterwards fails regardless of what the migration steps did with the
    // rest of the document. So in practice a damaged version number is not "pre-versioning" — it is
    // unreadable, and takes the same corrupt-file path as truncated JSON.

    @Test
    fun `a settingsVersion that is not a number makes the whole document unreadable`() {
        writeSettings("""{"settingsVersion":"not-a-number","theme":"dark"}""")

        val settings = SettingsManager().loadSettings()

        assertEquals(AppSettings().theme, settings.theme, "defaults are used rather than crashing")
        assertNotNull(
            backupFiles().firstOrNull { it.name.startsWith("settings.json.corrupt-") },
            "the original must still be preserved, exactly as for any other unreadable file",
        )
    }

    @Test
    fun `a settingsVersion that is an object rather than a number makes the whole document unreadable`() {
        writeSettings("""{"settingsVersion":{"nested":true},"theme":"dark"}""")

        val settings = SettingsManager().loadSettings()

        assertEquals(AppSettings().theme, settings.theme)
    }

    // ── Migrations only run for the versions a document actually needs ─────────

    @Test
    fun `a document already past version 1 does not have its screen-assignment fields re-migrated`() {
        // If this document's own version claims migration 1 already ran, its result must be
        // trusted rather than replayed — a raw showBible field left over from something else
        // entirely must not be reinterpreted as if it were still pre-migration.
        writeSettings(
            """{"settingsVersion":2,"projectionSettings":{"screenAssignments":[
               {"targetDisplay":0,"showBible":false}]}}""",
        )

        val settings = SettingsManager().loadSettings()

        assertEquals(
            "both",
            settings.projectionSettings.screenAssignments.single().bibleMode,
            "version 1 is marked as already applied to this document; migration 1 must not run a second time",
        )
    }

    @Test
    fun `a document already past version 2 still receives the later companion migrations`() {
        writeSettings(
            """{"settingsVersion":2,"companionSatelliteConnections":[
               {"name":"Deck","rows":2,"columns":6}]}""",
        )

        val settings = SettingsManager().loadSettings()

        assertEquals(
            2,
            settings.companionSatelliteConnections.single().tabRows,
            "migrations numbered above this document's own version must still run",
        )
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
    fun `a settings path that cannot be read at all still starts the app`() {
        // Not a truncated document but a path that throws on being read — a folder where the file
        // should be, which is what a botched sync or a restored backup can leave behind. The
        // startup path has to survive it, since there is no UI yet to report it to.
        appDir.mkdirs()
        settingsFile.mkdirs()

        val settings = SettingsManager().loadSettings()

        assertEquals(AppSettings.CURRENT_SETTINGS_VERSION, settings.settingsVersion)
        assertEquals(AppSettings().theme, settings.theme, "defaults rather than a failure to launch")
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
    fun `a second downgrade at the same future version does not overwrite the first snapshot`() {
        val future = AppSettings.CURRENT_SETTINGS_VERSION + 1
        writeSettings("""{"settingsVersion":$future,"theme":"first-load"}""")
        SettingsManager().loadSettings()
        val backup = backupFiles().single { it.name == "settings.json.v$future.bak" }
        val original = backup.readText()

        writeSettings("""{"settingsVersion":$future,"theme":"second-load"}""")
        SettingsManager().loadSettings()

        assertEquals(
            original,
            backup.readText(),
                "the oldest snapshot for a version is the one taken before any lossy rewrite, so it is the one " +
                    "worth keeping",
        )
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

    // ── Version 6: the bible translation stack ──────────────────────────────────

    @Test
    @Suppress("MaxLineLength")
    fun `an older file's bible pair becomes the translation stack`() {
        val migrated = SettingsManager().migrateAndDecode(
            """{"settingsVersion":5,"bibleSettings":{"primaryBible":"kjv.spb","secondaryBible":"rst.spb","primaryBibleColor":"#ABCDEF"}}""",
        )

        assertEquals(
            listOf("kjv.spb", "rst.spb"),
            migrated.bibleSettings.translations.map { it.fileName },
        )
        assertEquals(
            "#ABCDEF",
            migrated.bibleSettings.translations[0].textColor,
            "styling has to come across, or everyone's bible resets to the defaults on upgrade",
        )
    }

    @Test
    fun `an output naming one of the two bibles becomes a position`() {
        val migrated = SettingsManager().migrateAndDecode(
            """{"settingsVersion":5,"projectionSettings":{"screenAssignments":[
                {"targetDisplay":0,"bibleMode":"primary"},
                {"targetDisplay":1,"bibleMode":"secondary"},
                {"targetDisplay":2,"bibleMode":"both"},
                {"targetDisplay":3,"bibleMode":"off"}]}}""".trimIndent().replace("\n", ""),
        )
        val outputs = migrated.projectionSettings.screenAssignments

        assertEquals(listOf(0), outputs[0].bibleTranslations)
        assertEquals(listOf(1), outputs[1].bibleTranslations)
        assertEquals(
            emptyList(),
            outputs[2].bibleTranslations,
            "\"both\" is every translation, which is the empty list",
        )
        assertEquals(emptyList(), outputs[3].bibleTranslations)
        assertTrue(outputs[0].showBible, "naming a bible does not switch the output off")
        assertTrue(!outputs[3].showBible, "and an output that was off stays off")
    }

    // ── The stack is repaired on every load, not only on the version-6 upgrade ──
    //
    // `primaryBible`/`secondaryBible` are legacy mirrors of the first two of the stack. Anything that
    // writes one of them without going through `withTranslations` leaves a document at the *current*
    // version whose stack is empty and whose pair is not — and a current-version document takes the
    // early return above, so before this it was never repaired and never would be. Nothing surfaces
    // it either: `translationList()` falls back to the pair, so the app presents correctly until the
    // first stack edit rewrites the pair from a list that never held those bibles.

    @Test
    @Suppress("MaxLineLength")
    fun `a current-version file whose stack never got filled is repaired on load`() {
        val current = AppSettings.CURRENT_SETTINGS_VERSION
        val loaded = SettingsManager().migrateAndDecode(
            """{"settingsVersion":$current,"bibleSettings":{"primaryBible":"kjv.spb","secondaryBible":"rst.spb","translations":[]}}""",
        )

        assertEquals(
            listOf("kjv.spb", "rst.spb"),
            loaded.bibleSettings.translations.map { it.fileName },
            "the pair has to reach the stack, in order, or the first stack edit erases both bibles",
        )
    }

    @Test
    @Suppress("MaxLineLength")
    fun `a stack emptied on purpose is not refilled`() {
        // The other half of the repair, and what stops it being wrong: clearing the last translation
        // goes through `withTranslations`, which clears the legacy pair with it. An empty stack
        // beside an empty pair is a deliberate state, not drift, and must survive a reload.
        val current = AppSettings.CURRENT_SETTINGS_VERSION
        val loaded = SettingsManager().migrateAndDecode(
            """{"settingsVersion":$current,"bibleSettings":{"primaryBible":"","secondaryBible":"","translations":[]}}""",
        )

        assertTrue(
            loaded.bibleSettings.translations.isEmpty(),
            "nothing to put back, so nothing may be invented",
        )
    }

    @Test
    fun `a repaired load leaves a configured stack exactly as it was`() {
        val current = AppSettings.CURRENT_SETTINGS_VERSION
        val loaded = SettingsManager().migrateAndDecode(
            """{"settingsVersion":$current,"bibleSettings":{"primaryBible":"kjv.spb","secondaryBible":"rst.spb",
                "translations":[{"fileName":"rst.spb","textColor":"#ABCDEF"},{"fileName":"kjv.spb"}]}}"""
                .trimIndent().replace("\n", ""),
        )

        assertEquals(
            listOf("rst.spb", "kjv.spb"),
            loaded.bibleSettings.translations.map { it.fileName },
            "the stack is the source of truth; the repair must not reorder it to match the mirror",
        )
        assertEquals("#ABCDEF", loaded.bibleSettings.translations[0].textColor)
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

    @Test
    fun `importing a file from a newer build also writes no backup`() {
        // The downgrade path snapshots before rewriting too, but import passes no backup target
        // either way — this is the same "the user's file is not ours to touch" rule on the other
        // of the two version branches that can call backupBeforeRewrite.
        val future = AppSettings.CURRENT_SETTINGS_VERSION + 1
        val imported = SettingsManager().migrateAndDecode("""{"settingsVersion":$future,"theme":"dark"}""")

        assertEquals(future, imported.settingsVersion)
        assertTrue(backupFiles().isEmpty())
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

    // ── Translation-stack migration reaches every output list ───────────────────────────────────

    @Test
    fun `a browser source's bible mode is migrated alongside a screen's`() {
        // Browser sources are the same ScreenAssignment shape driven by the same UI, so they carry
        // the same legacy mode. Left unmigrated, a stream feed set to one language keeps a mode the
        // new code cannot read and silently starts showing every translation stacked.
        writeSettings(
            """{"settingsVersion":5,"projectionSettings":{
               "screenAssignments":[{"targetDisplay":1,"bibleMode":"primary"}],
               "browserSourceOutputs":[{"targetDisplay":0,"bibleMode":"secondary"}]}}""",
        )

        val projection = SettingsManager().loadSettings().projectionSettings

        assertEquals("both", projection.screenAssignments.single().bibleMode)
        assertEquals(listOf(0), projection.screenAssignments.single().bibleTranslations)
        assertEquals(
            "both", projection.browserSourceOutputs.single().bibleMode,
            "a browser source must not keep a mode the new code no longer understands",
        )
        assertEquals(
            listOf(1), projection.browserSourceOutputs.single().bibleTranslations,
            "\"secondary\" names position 1, on a browser source exactly as on a screen",
        )
    }

    // ── Version 7: chords moved from the stage monitor onto each output ─────────────────────────

    @Test
    fun `chords switched off globally are switched off on every output`() {
        writeSettings(
            """{"settingsVersion":6,"stageMonitorSettings":{"showChords":false},
               "projectionSettings":{
               "screenAssignments":[{"targetDisplay":0},{"targetDisplay":1}],
               "browserSourceOutputs":[{"targetDisplay":0}]}}""",
        )

        val projection = SettingsManager().loadSettings().projectionSettings

        assertTrue(
            projection.screenAssignments.none { it.showChords },
            "the one switch the operator turned off has to survive becoming a per-output one",
        )
        assertFalse(
            projection.browserSourceOutputs.single().showChords,
            "a browser source can be a stage monitor too, so it carries the same field",
        )
    }

    @Test
    fun `chords left on globally leave every output at the default`() {
        writeSettings(
            """{"settingsVersion":6,"stageMonitorSettings":{"showChords":true},
               "projectionSettings":{"screenAssignments":[{"targetDisplay":0}]}}""",
        )

        assertTrue(SettingsManager().loadSettings().projectionSettings.screenAssignments.single().showChords)
    }

    @Test
    fun `an output that already names its own chord setting is left alone`() {
        // A file written by this build and then hand-edited backwards: the per-output field is the
        // newer, more specific statement and must not be overwritten by the global it replaced.
        writeSettings(
            """{"settingsVersion":6,"stageMonitorSettings":{"showChords":false},
               "projectionSettings":{"screenAssignments":[
               {"targetDisplay":0,"showChords":true},{"targetDisplay":1}]}}""",
        )

        val assignments = SettingsManager().loadSettings().projectionSettings.screenAssignments

        assertTrue(assignments[0].showChords, "an explicit per-output value wins over the old global")
        assertFalse(assignments[1].showChords)
    }
}
