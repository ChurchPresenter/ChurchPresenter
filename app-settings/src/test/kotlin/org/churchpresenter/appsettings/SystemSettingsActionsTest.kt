package org.churchpresenter.appsettings

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.churchpresenter.server.InstanceLinkLogSide
import org.churchpresenter.server.InstanceLinkLogger
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.settings.OBSSettings
import org.churchpresenter.settings.SettingsManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Export, import, reset and clear-uploads, with a scripted [SettingsActionUi] answering for the
 * operator, over the real [SettingsManager] in a home of the test's own.
 */
class SystemSettingsActionsTest {

    private lateinit var home: File
    private lateinit var dir: File
    private var realHome: String? = null

    /** Answers as scripted and records what it was asked. Work sent to the UI thread waits in [pending]. */
    private class FakeUi(
        var savePath: Path? = null,
        var openPath: Path? = null,
        confirms: List<Boolean> = emptyList(),
        var option: Int = 0,
    ) : SettingsActionUi {
        private val answers = ArrayDeque(confirms)
        val suggestedNames = mutableListOf<String>()
        val messages = mutableListOf<Pair<String, SettingsMessageKind>>()
        val questions = mutableListOf<Pair<String, SettingsQuestionKind>>()
        val optionsOffered = mutableListOf<List<String>>()
        val pending = mutableListOf<() -> Unit>()
        var restarts = 0

        override suspend fun chooseSavePath(suggestedName: String, title: String): Path? {
            suggestedNames += suggestedName
            return savePath
        }

        override suspend fun chooseOpenPath(title: String): Path? = openPath

        override fun showMessage(message: String, title: String, kind: SettingsMessageKind) {
            messages += message to kind
        }

        override fun confirm(message: String, title: String, kind: SettingsQuestionKind): Boolean {
            questions += message to kind
            return answers.removeFirst()
        }

        override fun pickOption(question: String, title: String, options: Array<String>): Int {
            optionsOffered += options.toList()
            return option
        }

        override fun onUiThread(block: () -> Unit) {
            pending += block
        }

        override fun restart(companionServer: CompanionServer?) {
            restarts++
        }

        fun runPending() {
            val blocks = pending.toList()
            pending.clear()
            blocks.forEach { it() }
        }
    }

    private val secrets =
        SecretsChoice(question = "Whose passwords?", keep = "Mine", useFile = "File's", cancel = "Cancel")

    private val json = Json { encodeDefaults = true }

    @BeforeTest
    fun setUp() {
        // Pins the Instance Link log's path, resolved once per JVM, before the home is swapped.
        InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "test_home_latch")
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-settings-actions-home").toFile()
        System.setProperty("user.home", home.absolutePath)
        dir = Files.createTempDirectory("cp-settings-actions").toFile()
    }

    @AfterTest
    fun tearDown() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
        dir.deleteRecursively()
    }

    private fun saved(): AppSettings = SettingsManager().loadSettings()

    private fun save(settings: AppSettings) = SettingsManager().saveSettings(settings)

    private val withSecrets = AppSettings(
        obsSettings = OBSSettings(password = "obs-pass"),
        calendarSync = CalendarSyncSettings(enabled = true, desktopToken = "relay-token"),
        songFavoritesPanelHeightDp = 333,
    )

    private fun exported(path: Path): AppSettings = SettingsManager().migrateAndDecode(path.toFile().readText())

    private fun exportFile(settings: AppSettings, name: String = "in.json"): Path =
        File(dir, name).apply { writeText(json.encodeToString(AppSettings.serializer(), settings)) }.toPath()

    // ── Export ──────────────────────────────────────────────────────────────────

    @Test
    fun `an export the operator cancels writes nothing and says nothing`() = runBlocking<Unit> {
        val ui = FakeUi(savePath = null)

        exportSettings("Export", "Exported", "Failed", ui = ui)

        assertEquals(listOf("churchpresenter-settings.json"), ui.suggestedNames)
        assertTrue(ui.messages.isEmpty())
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `an export keeps the passwords, drops the calendar pairing, and fixes the extension`() = runBlocking<Unit> {
        save(withSecrets)
        val ui = FakeUi(savePath = File(dir, "backup.txt").toPath())

        exportSettings("Export", "Exported", "Failed", ui = ui)

        val written = File(dir, "backup.json").toPath()
        assertFalse(File(dir, "backup.txt").exists(), "the file is written as .json, not as named")
        val settings = exported(written)
        assertEquals("obs-pass", settings.obsSettings.password)
        assertEquals(333, settings.songFavoritesPanelHeightDp)
        assertEquals(CalendarSyncSettings(), settings.calendarSync)
        assertEquals(listOf("Exported" to SettingsMessageKind.INFO), ui.messages)
    }

    @Test
    fun `a safe export suggests its own name and leaves out every password`() = runBlocking<Unit> {
        save(withSecrets)
        val target = File(dir, "share.json").toPath()
        val ui = FakeUi(savePath = target)

        exportSettings("Export", "Exported", "Failed", withoutSecrets = true, ui = ui)

        assertEquals(listOf("churchpresenter-settings-no-passwords.json"), ui.suggestedNames)
        val settings = exported(target)
        assertEquals("", settings.obsSettings.password)
        assertEquals(CalendarSyncSettings(), settings.calendarSync)
        assertEquals(333, settings.songFavoritesPanelHeightDp)
    }

    @Test
    fun `an export that cannot be written says it failed`() = runBlocking<Unit> {
        val ui = FakeUi(savePath = File(dir, "no-such-folder/out.json").toPath())

        exportSettings("Export", "Exported", "Failed", ui = ui)

        assertEquals(listOf("Failed" to SettingsMessageKind.ERROR), ui.messages)
    }

    // ── Import ──────────────────────────────────────────────────────────────────

    private suspend fun import(ui: FakeUi) =
        importSettings("Import", "Replace everything?", "Failed", companionServer = null, secrets = secrets, ui = ui)

    @Test
    fun `an import the operator cancels at the picker asks nothing`() = runBlocking<Unit> {
        val ui = FakeUi(openPath = null)

        import(ui)

        assertTrue(ui.questions.isEmpty())
        assertEquals(0, ui.restarts)
    }

    @Test
    fun `an import the operator does not confirm changes nothing`() = runBlocking<Unit> {
        save(AppSettings(songFavoritesPanelHeightDp = 200))
        val ui = FakeUi(openPath = exportFile(AppSettings(songFavoritesPanelHeightDp = 444)), confirms = listOf(false))

        import(ui)

        assertEquals(listOf("Replace everything?" to SettingsQuestionKind.WARNING), ui.questions)
        assertEquals(200, saved().songFavoritesPanelHeightDp)
        assertEquals(0, ui.restarts)
    }

    @Test
    fun `a file with no passwords is taken whole, keeps this computer's, and restarts`() = runBlocking<Unit> {
        save(AppSettings(obsSettings = OBSSettings(password = "local")))
        val ui = FakeUi(openPath = exportFile(AppSettings(songFavoritesPanelHeightDp = 444)), confirms = listOf(true))

        import(ui)

        assertTrue(ui.optionsOffered.isEmpty(), "there are no passwords in the file to ask about")
        val settings = saved()
        assertEquals(444, settings.songFavoritesPanelHeightDp)
        assertEquals("local", settings.obsSettings.password)
        assertEquals(1, ui.restarts)
        assertTrue(ui.messages.isEmpty())
    }

    @Test
    fun `a file with passwords asks whose to keep, and keeping this computer's keeps them`() = runBlocking<Unit> {
        save(AppSettings(obsSettings = OBSSettings(password = "local")))
        val ui = FakeUi(openPath = exportFile(withSecrets), confirms = listOf(true), option = 0)

        import(ui)

        assertEquals(listOf(listOf("Mine", "File's", "Cancel")), ui.optionsOffered)
        val settings = saved()
        assertEquals("local", settings.obsSettings.password)
        assertEquals(333, settings.songFavoritesPanelHeightDp)
        assertEquals(1, ui.restarts)
    }

    @Test
    fun `a file with passwords takes the file's when asked to, but never its calendar pairing`() = runBlocking<Unit> {
        save(AppSettings(obsSettings = OBSSettings(password = "local")))
        val ui = FakeUi(openPath = exportFile(withSecrets), confirms = listOf(true), option = 1)

        import(ui)

        val settings = saved()
        assertEquals("obs-pass", settings.obsSettings.password)
        assertEquals("", settings.calendarSync.desktopToken)
        assertEquals(1, ui.restarts)
    }

    @Test
    fun `a file with passwords is not imported when the question is cancelled or closed`() = runBlocking<Unit> {
        save(AppSettings(songFavoritesPanelHeightDp = 200))
        for (answer in listOf(2, -1)) {
            val ui = FakeUi(openPath = exportFile(withSecrets), confirms = listOf(true), option = answer)

            import(ui)

            assertEquals(200, saved().songFavoritesPanelHeightDp)
            assertEquals(0, ui.restarts)
            assertTrue(ui.messages.isEmpty())
        }
    }

    @Test
    fun `a file that is not settings says the import failed and restarts nothing`() = runBlocking<Unit> {
        val junk = File(dir, "junk.json").apply { writeText("not json") }
        val ui = FakeUi(openPath = junk.toPath(), confirms = listOf(true))

        import(ui)

        assertEquals(listOf("Failed" to SettingsMessageKind.ERROR), ui.messages)
        assertEquals(0, ui.restarts)
    }

    // ── Reset ───────────────────────────────────────────────────────────────────

    private fun lottieCache(): File =
        File(SettingsManager().lottiePresetsDir, "cached.json").apply { writeText("{}") }

    @Test
    fun `a reset waits for the UI thread, and declined changes nothing`() {
        save(AppSettings(songFavoritesPanelHeightDp = 200))
        val ui = FakeUi(confirms = listOf(false))

        resetAllSettings("Reset", "Reset everything?", "Clear the cache?", companionServer = null, ui = ui)
        assertTrue(ui.questions.isEmpty(), "nothing is asked until the UI thread runs it")
        ui.runPending()

        assertEquals(listOf("Reset everything?" to SettingsQuestionKind.WARNING), ui.questions)
        assertEquals(200, saved().songFavoritesPanelHeightDp)
        assertEquals(0, ui.restarts)
    }

    @Test
    fun `a reset that clears the cache deletes the lottie presets, writes defaults and restarts`() {
        save(AppSettings(songFavoritesPanelHeightDp = 200))
        val cached = lottieCache()
        val ui = FakeUi(confirms = listOf(true, true))

        resetAllSettings("Reset", "Reset everything?", "Clear the cache?", companionServer = null, ui = ui)
        ui.runPending()

        assertEquals("Clear the cache?" to SettingsQuestionKind.QUESTION, ui.questions[1])
        assertFalse(cached.parentFile.exists(), "the cache folder is gone")
        assertEquals(AppSettings().songFavoritesPanelHeightDp, saved().songFavoritesPanelHeightDp)
        assertEquals(1, ui.restarts)
    }

    @Test
    fun `a reset that keeps the cache leaves the lottie presets`() {
        save(AppSettings(songFavoritesPanelHeightDp = 200))
        val cached = lottieCache()
        val ui = FakeUi(confirms = listOf(true, false))

        resetAllSettings("Reset", "Reset everything?", "Clear the cache?", companionServer = null, ui = ui)
        ui.runPending()

        assertTrue(cached.exists())
        assertEquals(AppSettings().songFavoritesPanelHeightDp, saved().songFavoritesPanelHeightDp)
        assertEquals(1, ui.restarts)
    }

    // ── Clear remote uploads ────────────────────────────────────────────────────

    private val appDir get() = File(home, ".churchpresenter")

    private fun uploads(): List<File> = listOf("device_uploads", "device_presentations", "device_media").map {
        File(appDir, "$it/phone/file.bin").apply { parentFile.mkdirs(); writeText("x") }.parentFile.parentFile
    }

    @Test
    fun `clearing uploads declined deletes nothing`() {
        val trees = uploads()
        val ui = FakeUi(confirms = listOf(false))

        clearRemoteUploads("Clear", "Delete uploads?", "Cleared", ui = ui)
        ui.runPending()

        assertTrue(trees.all { it.exists() })
        assertTrue(ui.messages.isEmpty())
    }

    @Test
    fun `clearing uploads deletes all three trees and nothing else, then says so`() {
        val trees = uploads()
        val other = File(appDir, "lottie_presets/keep.json").apply { parentFile.mkdirs(); writeText("{}") }
        val ui = FakeUi(confirms = listOf(true))

        clearRemoteUploads("Clear", "Delete uploads?", "Cleared", ui = ui)
        assertTrue(trees.all { it.exists() }, "nothing is deleted until the UI thread runs it")
        ui.runPending()

        assertTrue(trees.none { it.exists() })
        assertTrue(other.exists())
        assertEquals(listOf("Delete uploads?" to SettingsQuestionKind.WARNING), ui.questions)
        assertEquals(listOf("Cleared" to SettingsMessageKind.INFO), ui.messages)
    }
}
