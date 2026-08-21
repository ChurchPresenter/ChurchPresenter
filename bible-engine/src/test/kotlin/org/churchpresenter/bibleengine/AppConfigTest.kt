package org.churchpresenter.bibleengine

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading `bible-engine.properties`, and discovering the Bible folder from the app's own settings.
 *
 * The engine runs headless beside the app, so a misread config means it silently listens on the
 * wrong port or finds no Bibles — with no UI to show an error in. The behaviours worth pinning are
 * that a missing config writes a commented default rather than failing, and that a blank or
 * malformed value leaves the existing setting alone instead of overwriting it with nothing.
 */
class AppConfigTest {

    private val temp: File = Files.createTempDirectory("ble-config-test").toFile()
    private lateinit var savedHome: String
    private lateinit var saved: Triple<String, String, Int>

    @BeforeTest
    fun captureState() {
        savedHome = System.getProperty("user.home")
        saved = Triple(Config.sttServerUrl, Config.bibleRoot, Config.outputPort)
    }

    @AfterTest
    fun restoreState() {
        System.setProperty("user.home", savedHome)
        Config.sttServerUrl = saved.first
        Config.bibleRoot = saved.second
        Config.outputPort = saved.third
        temp.deleteRecursively()
    }

    private fun props(contents: String): File =
        File(temp, "bible-engine.properties").apply { writeText(contents.trimIndent()) }

    // ── Loading ───────────────────────────────────────────────────────────────

    @Test
    fun `every setting is read from the file`() {
        AppConfig.load(
            props(
                """
                stt.server.url=http://localhost:5000
                bible.root=/tmp/bibles
                output.port=9999
                """
            )
        )
        assertEquals("http://localhost:5000", Config.sttServerUrl)
        assertEquals("/tmp/bibles", Config.bibleRoot)
        assertEquals(9999, Config.outputPort)
    }

    @Test
    fun `a blank value leaves the existing setting alone`() {
        // The shipped default file has every key present but empty — reading those as ""
        // would wipe the auto-discovered Bible root.
        Config.bibleRoot = "/previously/discovered"
        AppConfig.load(props("bible.root=\nstt.server.url=  "))
        assertEquals("/previously/discovered", Config.bibleRoot)
    }

    @Test
    fun `an unparseable port is ignored rather than zeroing the port`() {
        Config.outputPort = 8765
        AppConfig.load(props("output.port=not-a-number"))
        assertEquals(8765, Config.outputPort, "binding to port 0 would be worse than ignoring it")
    }

    @Test
    fun `an absent key leaves its setting untouched`() {
        Config.outputPort = 8765
        AppConfig.load(props("stt.server.url=http://example.test"))
        assertEquals(8765, Config.outputPort)
    }

    @Test
    fun `unknown keys are ignored`() {
        AppConfig.load(props("something.we.do.not.know=1\noutput.port=7777"))
        assertEquals(7777, Config.outputPort)
    }

    // ── First run ─────────────────────────────────────────────────────────────

    @Test
    fun `a missing config file is created with commented defaults`() {
        val file = File(temp, "fresh.properties")
        assertTrue(!file.exists())
        AppConfig.load(file)

        assertTrue(file.exists(), "the operator gets a file to edit rather than nothing")
        val text = file.readText()
        for (key in listOf("stt.server.url", "bible.root", "output.port")) {
            assertTrue(text.contains(key), "$key is present to be filled in")
        }
        assertTrue(text.contains("#"), "and is explained in comments")
    }

    @Test
    fun `the written default is itself loadable`() {
        // A default file the loader cannot read would strand the engine on first run.
        val file = File(temp, "fresh.properties")
        AppConfig.load(file)
        Config.outputPort = 1
        AppConfig.load(file)
        assertEquals(8765, Config.outputPort, "the documented default port is applied")
    }

    // ── Bible root discovery ──────────────────────────────────────────────────

    private fun settingsJson(contents: String) {
        System.setProperty("user.home", temp.absolutePath)
        File(temp, ".churchpresenter").mkdirs()
        File(temp, ".churchpresenter/settings.json").writeText(contents)
    }

    @Test
    fun `the bible folder is discovered from the app's settings file`() {
        settingsJson("""{"bibleSettings":{"storageDirectory":"/music/bibles"}}""")
        assertEquals("/music/bibles", AppConfig.discoverBibleRoot())
    }

    @Test
    fun `unrelated settings keys do not confuse the lookup`() {
        settingsJson("""{"other":{"x":1},"bibleSettings":{"storageDirectory":"/b","somethingElse":2}}""")
        assertEquals("/b", AppConfig.discoverBibleRoot())
    }

    @Test
    fun `a missing settings file discovers nothing`() {
        System.setProperty("user.home", temp.absolutePath)
        assertNull(AppConfig.discoverBibleRoot())
    }

    @Test
    fun `a malformed settings file discovers nothing rather than throwing`() {
        settingsJson("{ this is not json")
        assertNull(AppConfig.discoverBibleRoot(), "the engine still starts, just without a Bible root")
    }

    @Test
    fun `a settings file without the key discovers nothing`() {
        settingsJson("""{"bibleSettings":{}}""")
        assertNull(AppConfig.discoverBibleRoot())
    }

    @Test
    fun `a blank storage directory is treated as unset`() {
        settingsJson("""{"bibleSettings":{"storageDirectory":"  "}}""")
        assertNull(AppConfig.discoverBibleRoot())
    }

    @Test
    fun `the config file is looked for by name`() {
        assertEquals("bible-engine.properties", AppConfig.findConfigFile().name)
    }
}
