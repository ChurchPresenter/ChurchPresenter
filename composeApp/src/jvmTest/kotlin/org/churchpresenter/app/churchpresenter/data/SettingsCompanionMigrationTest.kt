package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two Companion Satellite migrations, which are the ones that can silently reset a Stream Deck.
 *
 * Both exist for the same reason: a field was renamed or replaced, and `ignoreUnknownKeys` means the
 * old name is dropped without a word rather than failing to parse. What the user sees is a surface
 * that reconnects on the wrong grid — a 2×6 deck registering itself as the default 4×8 — which
 * Companion accepts, so nothing anywhere reports an error. It is only visible as buttons landing in
 * the wrong places during a service.
 *
 * Version 3 renames the single pre-placement `rows`/`columns`/`bitmapSize` onto the tab placement,
 * which was the only placement that existed then. Version 4 converts the briefly-shipped
 * start/end ROW-RANGE fields back into a plain count, and strips the range keys either way so they
 * do not linger forever as unknown keys.
 *
 * Both are guarded so they only touch entries that actually carry the old fields, and both must be
 * idempotent — an older build re-saving the file strips the version and replays the whole chain.
 * The rest of the settings pipeline is covered by [SettingsManagerTest].
 */
class SettingsCompanionMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-settings-companion-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    /** Decodes [connectionJson] as the one configured connection of a pre-versioning settings file. */
    private fun connection(connectionJson: String): CompanionSatelliteSettings =
        SettingsManager()
            .migrateAndDecode("""{"companionSatelliteConnections":[$connectionJson]}""")
            .companionSatelliteConnections
            .single()

    private val defaults = CompanionSatelliteSettings()

    // ── Version 3: the single grid becomes the tab grid ─────────────────────────

    @Test
    fun `a grid configured before placements existed becomes the tab grid`() {
        val migrated = connection("""{"name":"Stream Deck","rows":2,"columns":6,"bitmapSize":120}""")

        assertEquals(2, migrated.tabRows, "the deck reconnects on this grid — a reset here misplaces every button")
        assertEquals(6, migrated.tabColumns)
        assertEquals(120, migrated.tabBitmapSize)
        assertEquals("Stream Deck", migrated.name, "the rest of the connection is carried across untouched")
    }

    @Test
    fun `the other placements keep their defaults`() {
        val migrated = connection("""{"rows":2,"columns":6}""")

        assertEquals(defaults.leftSidebarRows, migrated.leftSidebarRows, "the tab was the only placement that existed")
        assertEquals(defaults.rightSidebarColumns, migrated.rightSidebarColumns)
    }

    @Test
    fun `a value already stored under the new name wins`() {
        val migrated = connection("""{"rows":2,"tabRows":9,"columns":6}""")

        assertEquals(9, migrated.tabRows, "the new field is the one the user configured most recently")
        assertEquals(6, migrated.tabColumns, "the other old fields still migrate")
    }

    @Test
    fun `a connection carrying none of the old fields is left as it stands`() {
        val migrated = connection("""{"name":"Modern","tabRows":3,"tabColumns":5}""")

        assertEquals(3, migrated.tabRows)
        assertEquals(5, migrated.tabColumns)
        assertEquals("Modern", migrated.name)
    }

    @Test
    fun `only the connection that needs migrating is rewritten`() {
        val migrated = SettingsManager().migrateAndDecode(
            """{"companionSatelliteConnections":[
                {"name":"Old","rows":2,"columns":6},
                {"name":"New","tabRows":3,"tabColumns":5}
            ]}""",
        ).companionSatelliteConnections

        assertEquals(listOf("Old", "New"), migrated.map { it.name }, "the order of configured surfaces is kept")
        assertEquals(2, migrated[0].tabRows)
        assertEquals(3, migrated[1].tabRows)
    }

    // ── Version 4: a row range becomes a row count ──────────────────────────────

    @Test
    fun `a configured row and column range becomes the same size grid`() {
        val migrated = connection(
            """{"tabStartRow":0,"tabEndRow":3,"tabStartColumn":0,"tabEndColumn":7}""",
        )

        assertEquals(4, migrated.tabRows, "rows 0..3 is four rows")
        assertEquals(8, migrated.tabColumns, "columns 0..7 is eight columns")
    }

    @Test
    fun `a range that did not start at zero keeps its size rather than its offset`() {
        // The offset is deliberately dropped — Companion's own per-surface start page replaced it.
        val migrated = connection("""{"tabStartRow":2,"tabEndRow":3,"tabStartColumn":4,"tabEndColumn":7}""")

        assertEquals(2, migrated.tabRows)
        assertEquals(4, migrated.tabColumns)
    }

    @Test
    fun `a single row range is one row rather than none`() {
        val migrated = connection("""{"tabStartRow":1,"tabEndRow":1,"tabStartColumn":3,"tabEndColumn":3}""")

        assertEquals(1, migrated.tabRows)
        assertEquals(1, migrated.tabColumns)
    }

    @Test
    fun `a back-to-front range still leaves a usable grid`() {
        val migrated = connection("""{"tabStartRow":5,"tabEndRow":1,"tabStartColumn":9,"tabEndColumn":2}""")

        assertTrue(migrated.tabRows >= 1, "a zero-row grid would register a surface with no buttons at all")
        assertTrue(migrated.tabColumns >= 1)
    }

    @Test
    fun `each placement's range is converted separately`() {
        val migrated = connection(
            """{"tabStartRow":0,"tabEndRow":1,"tabStartColumn":0,"tabEndColumn":2,
                "leftSidebarStartRow":0,"leftSidebarEndRow":5,"leftSidebarStartColumn":0,"leftSidebarEndColumn":1,
                "rightSidebarStartRow":0,"rightSidebarEndRow":2,"rightSidebarStartColumn":0,"rightSidebarEndColumn":3}""",
        )

        assertEquals(2 to 3, migrated.tabRows to migrated.tabColumns)
        assertEquals(6 to 2, migrated.leftSidebarRows to migrated.leftSidebarColumns)
        assertEquals(3 to 4, migrated.rightSidebarRows to migrated.rightSidebarColumns)
    }

    @Test
    fun `a count already stored alongside a range wins`() {
        val migrated = connection("""{"tabStartRow":0,"tabEndRow":9,"tabRows":3}""")

        assertEquals(3, migrated.tabRows, "the range was the experiment; the count is what the user set after it")
    }

    @Test
    fun `half a range converts nothing but is still cleared away`() {
        // An end with no start cannot say how big the grid was, so the default stands.
        val migrated = connection("""{"name":"Half","tabEndRow":3,"tabEndColumn":7}""")

        assertEquals(defaults.tabRows, migrated.tabRows)
        assertEquals(defaults.tabColumns, migrated.tabColumns)
        assertEquals("Half", migrated.name)
    }

    @Test
    fun `the range fields do not survive into the saved file`() {
        val manager = SettingsManager()
        File(home, ".churchpresenter").mkdirs()
        File(home, ".churchpresenter/settings.json").writeText(
            """{"companionSatelliteConnections":[
                {"tabStartRow":0,"tabEndRow":1,"tabStartColumn":0,"tabEndColumn":2}
            ]}""",
        )

        manager.saveSettings(manager.loadSettings())

        val saved = File(home, ".churchpresenter/settings.json").readText()
        assertTrue("tabStartRow" !in saved, "a dead field left in the file would be migrated again on every load")
        assertTrue("tabEndColumn" !in saved)
        assertTrue("\"tabRows\":2" in saved, "and the size it stood for has to be there instead")
    }

    // ── Both together, and doing it twice ───────────────────────────────────────

    @Test
    fun `a file old enough to need both migrations gets both`() {
        val migrated = connection(
            """{"name":"Ancient","rows":2,"columns":6,"bitmapSize":96,
                "leftSidebarStartRow":0,"leftSidebarEndRow":3,"leftSidebarStartColumn":0,"leftSidebarEndColumn":1}""",
        )

        assertEquals(2, migrated.tabRows, "the pre-placement grid")
        assertEquals(6, migrated.tabColumns)
        assertEquals(96, migrated.tabBitmapSize)
        assertEquals(4, migrated.leftSidebarRows, "and the short-lived range on another placement")
        assertEquals(2, migrated.leftSidebarColumns)
    }

    @Test
    fun `running the migrations a second time changes nothing`() {
        // An older build ignores settingsVersion, strips it on save, and the whole chain replays.
        val manager = SettingsManager()
        val once = manager.migrateAndDecode(
            """{"companionSatelliteConnections":[{"rows":2,"columns":6,"tabStartRow":0,"tabEndRow":1}]}""",
        )

        val json = SettingsManager().let { it.saveSettings(once); File(home, ".churchpresenter/settings.json").readText() }
        val twice = manager.migrateAndDecode(json.replace(Regex(""""settingsVersion":\d+,?"""), ""))

        assertEquals(
            once.companionSatelliteConnections,
            twice.companionSatelliteConnections,
            "a migration that is not idempotent halves the grid every time the app is opened",
        )
    }

    @Test
    fun `a truncated file mentioning a connection is preserved rather than half-migrated`() {
        // The migrations are triggered by a substring match before the document is parsed, so a
        // file cut short mid-write still enters them. Each hands the text back untouched, and the
        // failure is then handled where every other unreadable settings file is.
        val appDir = File(home, ".churchpresenter").also { it.mkdirs() }
        File(appDir, "settings.json").writeText(
            """{"theme":"dark","companionSatelliteConnections":[{"rows":2,"tabStartRow":0,"showBible":fal""",
        )

        val settings = SettingsManager().loadSettings()

        assertEquals(AppSettings().theme, settings.theme, "defaults are used rather than a partly-rewritten document")
        assertTrue(
            appDir.listFiles()?.any { it.name.startsWith("settings.json.corrupt-") } == true,
            "and the original is kept: found ${appDir.listFiles()?.map { it.name }}",
        )
    }

    @Test
    fun `a settings file with no companion connections at all is untouched`() {
        val settings = SettingsManager().migrateAndDecode("""{"theme":"dark"}""")

        assertEquals("dark", settings.theme)
        assertEquals(
            AppSettings().companionSatelliteConnections.size,
            settings.companionSatelliteConnections.size,
            "a church with no Stream Deck still gets the default connection entry",
        )
    }
}
