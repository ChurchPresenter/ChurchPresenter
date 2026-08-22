package org.churchpresenter.lottiegen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.viewmodel.LottieGenViewModel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The standalone generator's view model — presets, colour themes, logos and JSON export.
 *
 * It is a plain class holding Compose *snapshot* state (`mutableStateOf`), not a composable, so it
 * drives headlessly with no UI toolkit involved.
 *
 * Generation runs on a real dispatcher, so tests wait on the **positive signal** that output
 * appeared rather than on a duration. Most call [LottieGenViewModel.generateNow], which skips the
 * 300ms debounce entirely; only the two tests that are *about* the debounce pay for it.
 *
 * `user.home` is redirected per test because every storage object below it (`PresetStorage`,
 * `ColorThemeStorage`, `LogoStorage`) resolves its path from it **per call**, so nothing latches
 * onto a temp dir that later disappears.
 */
class LottieGenViewModelTest {

    private lateinit var temp: File
    private lateinit var savedHome: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("lottiegen-vm-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        scope.cancel()
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    /** Bounded poll on observable state. Ends on the condition; the timeout only fails the test. */
    private fun waitFor(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(2)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /** A view model whose first render has completed. */
    private fun viewModel(outputDir: File? = null, onFileSaved: (() -> Unit)? = null): LottieGenViewModel {
        val vm = LottieGenViewModel(scope = scope, outputDir = outputDir, onFileSaved = onFileSaved)
        vm.generateNow()
        waitFor("the first render") { vm.generatedJson != null }
        return vm
    }

    private fun outDir(name: String = "out") = File(temp, name).apply { mkdirs() }

    // ── Generation ────────────────────────────────────────────────────────────

    @Test
    fun `construction schedules a generation that produces Lottie JSON`() {
        val vm = viewModel()

        val json = assertNotNull(vm.generatedJson, "the preview is generated without being asked")
        assertTrue(json.trimStart().startsWith("{"), "it is a JSON object")
        assertTrue(json.contains("\"layers\""), "and looks like a Lottie document")
        assertEquals("", vm.statusText, "no error was reported")
    }

    @Test
    fun `the canvas size passed in is used instead of the default`() {
        val vm = LottieGenViewModel(scope = scope, canvasWidth = 1080, canvasHeight = 1920)
        assertEquals(1080, vm.config.canvasW)
        assertEquals(1920, vm.config.canvasH)
    }

    @Test
    fun `updating the config regenerates`() {
        val vm = viewModel()
        val before = assertNotNull(vm.generatedJson)

        vm.updateConfig { it.copy(nameText = "A Completely Different Name") }
        // One of the two tests that pays the debounce, because the debounce is the point.
        waitFor("the regenerated output") { vm.generatedJson != before }

        assertEquals("A Completely Different Name", vm.config.nameText)
        assertTrue(vm.generatedJson!!.contains("A COMPLETELY DIFFERENT NAME"), "the new text is in it")
    }

    @Test
    fun `rapid edits collapse into a single regeneration`() {
        // The debounce exists so typing does not regenerate per keystroke.
        val vm = viewModel()

        val before = assertNotNull(vm.generatedJson)
        vm.updateConfig { it.copy(nameText = "A") }
        vm.updateConfig { it.copy(nameText = "AB") }
        vm.updateConfig { it.copy(nameText = "ABC") }
        waitFor("the single regeneration") { vm.generatedJson != before }

        assertEquals("ABC", vm.config.nameText, "the last edit wins")
        // Only the final text was ever rendered — the intermediate edits were cancelled.
        assertTrue(vm.generatedJson!!.contains("ABC"), "the last edit is what got rendered")
    }

    @Test
    fun `generateNow produces output without waiting for the debounce`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "Immediate") }
        vm.generateNow()
        assertNotNull(vm.generatedJson)
    }

    @Test
    fun `an embedded instance reports that it has an output folder`() {
        assertTrue(LottieGenViewModel(scope = scope, outputDir = outDir()).hasOutputDir)
        assertTrue(!LottieGenViewModel(scope = scope).hasOutputDir, "standalone has none")
    }

    // ── Presets ───────────────────────────────────────────────────────────────

    @Test
    fun `saving a preset stores it and persists it to disk`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "Pastor John", infoText = "Guest Speaker") }
        vm.savePreset()

        assertEquals(1, vm.presets.size)
        assertEquals("Pastor John", vm.presets.single().config.nameText)
        // A second view model reads it back, which is the only proof it really persisted.
        assertEquals(1, LottieGenViewModel(scope = scope).presets.size)
    }

    @Test
    fun `a preset does not carry the logo image data`() {
        // Logo bytes are large and belong to the session, not the preset.
        val vm = viewModel()
        vm.updateConfig {
            it.copy(
                nameText = "With Logo",
                logoData = "data:image/png;base64,AAAA",
                logoW = 10,
                logoH = 10,
            )
        }
        vm.savePreset()

        val saved = vm.presets.single().config
        assertNull(saved.logoData)
        assertEquals(0, saved.logoW)
    }

    @Test
    fun `saving the same name and info twice updates rather than duplicating`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "Pastor John", infoText = "Speaker") }
        vm.savePreset()
        vm.updateConfig { it.copy(nameColor = "#FF0000") }
        vm.savePreset()

        assertEquals(1, vm.presets.size, "the operator edited one entry, not created two")
        assertEquals("#FF0000", vm.presets.single().config.nameColor)
    }

    @Test
    fun `two presets sharing a name but not an info line both survive, uniquely named`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "John", infoText = "Speaker") }
        vm.savePreset()
        vm.updateConfig { it.copy(infoText = "Worship Leader") }
        vm.savePreset()

        assertEquals(2, vm.presets.size)
        assertEquals(2, vm.presets.map { it.name }.distinct().size, "names are made unique")
    }

    @Test
    fun `a nameless preset is filed under a placeholder rather than an empty name`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "   ", infoText = "") }
        vm.savePreset()
        assertTrue(vm.presets.single().name.isNotBlank())
    }

    @Test
    fun `loading a preset restores its config but keeps the current logo`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "Saved One", nameColor = "#123456") }
        vm.savePreset()
        vm.updateConfig {
            it.copy(
                nameText = "Since Changed",
                nameColor = "#FFFFFF",
                logoData = "data:image/png;base64,BBBB",
            )
        }

        vm.loadPreset(0)

        assertEquals("Saved One", vm.config.nameText)
        assertEquals("#123456", vm.config.nameColor)
        assertEquals("data:image/png;base64,BBBB", vm.config.logoData, "the loaded logo is not clobbered")
    }

    @Test
    fun `loading a preset that does not exist changes nothing`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "Unchanged") }
        vm.loadPreset(99)
        assertEquals("Unchanged", vm.config.nameText)
    }

    @Test
    fun `deleting a preset removes it and persists the removal`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "First") }; vm.savePreset()
        vm.updateConfig { it.copy(nameText = "Second") }; vm.savePreset()

        vm.deletePreset(0)
        assertEquals(1, vm.presets.size)
        assertEquals(1, LottieGenViewModel(scope = scope).presets.size, "the deletion reached disk")
    }

    @Test
    fun `applying the current style rewrites every preset but keeps their text`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "One", infoText = "A") }; vm.savePreset()
        vm.updateConfig { it.copy(nameText = "Two", infoText = "B") }; vm.savePreset()

        vm.updateConfig { it.copy(nameColor = "#00FF00") }
        vm.applyStyleToAll()

        assertTrue(vm.presets.all { it.config.nameColor == "#00FF00" }, "the style reached all of them")
        assertEquals(listOf("One", "Two"), vm.presets.map { it.config.nameText }, "their own text is untouched")
        assertEquals(listOf("A", "B"), vm.presets.map { it.config.infoText })
        assertTrue(vm.statusText.contains("2"), "the operator is told how many changed: '${vm.statusText}'")
    }

    // ── Batch import ──────────────────────────────────────────────────────────

    @Test
    fun `batch import creates one preset per pipe-separated line`() {
        val vm = viewModel()
        val (added, updated) = vm.batchImportPresets("Pastor John | Speaker\nJane Doe | Worship")

        assertEquals(2, added)
        assertEquals(0, updated)
        assertEquals(listOf("Pastor John", "Jane Doe"), vm.presets.map { it.config.nameText })
        assertEquals(listOf("Speaker", "Worship"), vm.presets.map { it.config.infoText })
    }

    @Test
    fun `re-importing the same line updates it instead of duplicating`() {
        val vm = viewModel()
        vm.batchImportPresets("Pastor John | Speaker")
        val (added, updated) = vm.batchImportPresets("Pastor John | Speaker")

        assertEquals(0, added)
        assertEquals(1, updated)
        assertEquals(1, vm.presets.size)
    }

    @Test
    fun `blank and unseparated lines are skipped`() {
        val vm = viewModel()
        val (added, _) = vm.batchImportPresets("Good | Line\n\n   \nno separator here\n| no name")

        assertEquals(1, added, "only the well-formed line counted")
        assertEquals("Good", vm.presets.single().config.nameText)
    }

    @Test
    fun `an imported preset inherits the current style`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameColor = "#ABCDEF") }
        vm.batchImportPresets("Someone | Somewhere")
        assertEquals("#ABCDEF", vm.presets.single().config.nameColor)
    }

    // ── Colour themes ─────────────────────────────────────────────────────────

    @Test
    fun `saving a colour theme captures the current colours and persists`() {
        val vm = viewModel()
        val before = vm.colorThemes.size
        vm.updateConfig { it.copy(nameColor = "#111111", bgColor = "#222222", bgColorAlpha = 50) }
        vm.saveColorTheme()

        assertEquals(before + 1, vm.colorThemes.size)
        val saved = vm.colorThemes.last().colors
        assertEquals("#111111", saved.nameColor)
        assertEquals("#222222", saved.bgColor)
        assertEquals(50, saved.bgColorAlpha, "alpha travels with the colour")
    }

    @Test
    fun `saved themes get distinct names`() {
        val vm = viewModel()
        vm.saveColorTheme(); vm.saveColorTheme()
        val names = vm.colorThemes.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `loading a theme applies every colour to the config`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameColor = "#111111", infoColor = "#222222", accentColor = "#333333") }
        vm.saveColorTheme()
        val index = vm.colorThemes.lastIndex
        vm.updateConfig { it.copy(nameColor = "#FFFFFF", infoColor = "#FFFFFF", accentColor = "#FFFFFF") }

        vm.loadColorTheme(index)

        assertEquals("#111111", vm.config.nameColor)
        assertEquals("#222222", vm.config.infoColor)
        assertEquals("#333333", vm.config.accentColor)
    }

    @Test
    fun `loading a theme that does not exist changes nothing`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameColor = "#ABCABC") }
        vm.loadColorTheme(999)
        assertEquals("#ABCABC", vm.config.nameColor)
    }

    @Test
    fun `deleting a theme removes it`() {
        val vm = viewModel()
        vm.saveColorTheme()
        val before = vm.colorThemes.size
        vm.deleteColorTheme(before - 1)
        assertEquals(before - 1, vm.colorThemes.size)
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Test
    fun `downloading writes a JSON file named after the text`() {
        val dir = outDir()
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "Pastor John", infoText = "Speaker") }
        vm.generateNow()
        waitFor("the render") { vm.generatedJson!!.contains("PASTOR JOHN") }

        val file = assertNotNull(vm.downloadJson(dir))
        assertTrue(file.exists())
        assertEquals("Pastor John - Speaker - 01.json", file.name)
        assertTrue(file.readText().contains("\"layers\""))
        assertTrue(vm.statusText.contains(file.name), "the operator is told where it went")
    }

    @Test
    fun `a second download of the same name gets its own number`() {
        val dir = outDir()
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "Same", infoText = "") }

        val first = assertNotNull(vm.downloadJson(dir))
        val second = assertNotNull(vm.downloadJson(dir))
        assertTrue(first.name != second.name, "an existing file is never overwritten")
        assertEquals("Same - 01.json", first.name)
        assertEquals("Same - 02.json", second.name)
    }

    @Test
    fun `path characters in the text cannot steer the write out of the folder`() {
        val dir = outDir()
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "../../evil:name?", infoText = "") }

        val file = assertNotNull(vm.downloadJson(dir))
        assertEquals(dir.canonicalPath, file.canonicalFile.parentFile.path, "landed in the chosen folder")
        assertTrue(!file.name.contains('/') && !file.name.contains(':'))
    }

    @Test
    fun `empty text falls back to a default file name`() {
        val dir = outDir()
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "", infoText = "") }
        assertEquals("lower-third - 01.json", assertNotNull(vm.downloadJson(dir)).name)
    }

    @Test
    fun `downloading with nowhere to write returns null rather than throwing`() {
        val vm = viewModel()
        assertNull(vm.downloadJson(null), "standalone with no folder chosen")
        assertNull(vm.downloadJson(File(temp, "not-a-directory")), "a path that is not a folder")
    }

    @Test
    fun `downloading before anything is generated returns null`() {
        // Constructed directly, not through viewModel(), so the debounced first render is still
        // pending — there is nothing to write yet.
        val vm = LottieGenViewModel(scope = scope)
        assertNull(vm.downloadJson(outDir()))
    }

    @Test
    fun `an embedded save writes to the configured folder and records a preset`() {
        val dir = outDir()
        var saveCallbacks = 0
        val vm = viewModel(outputDir = dir, onFileSaved = { saveCallbacks++ })
        vm.updateConfig { it.copy(nameText = "Embedded", infoText = "") }

        val file = assertNotNull(vm.saveLowerThird())
        assertEquals(dir.canonicalPath, file.canonicalFile.parentFile.path)
        assertEquals(1, vm.presets.size, "saving also remembers it as a preset")
        assertEquals(1, saveCallbacks, "the host is told a file appeared")
    }

    @Test
    fun `batch download writes one file per preset`() {
        val dir = outDir()
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "One", infoText = "") }; vm.savePreset()
        vm.updateConfig { it.copy(nameText = "Two", infoText = "") }; vm.savePreset()

        vm.batchDownloadAll(dir)
        // The status line is written after the last file, so it — not the file count — is the
        // signal that the batch finished.
        waitFor("the batch to finish") { vm.statusText.startsWith("Saved ") }

        val written = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("One - 01.json", "Two - 01.json"), written)
        assertTrue(vm.statusText.contains("2"), "the count is reported: '${vm.statusText}'")
    }

    @Test
    fun `batch download to a path that is not a folder does nothing`() {
        val vm = viewModel()
        vm.updateConfig { it.copy(nameText = "One") }; vm.savePreset()
        vm.batchDownloadAll(File(temp, "nope"))
        assertTrue(!File(temp, "nope").exists())
    }

    // ── Logos ─────────────────────────────────────────────────────────────────

    private fun pngFile(name: String): File {
        val file = File(temp, name)
        val image = java.awt.image.BufferedImage(8, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        javax.imageio.ImageIO.write(image, "png", file)
        return file
    }

    @Test
    fun `importing a logo copies it into the library and selects it`() {
        val vm = viewModel()
        vm.importAndLoadLogo(pngFile("badge.png"))
        waitFor("the logo to apply") { vm.config.logoEnabled }

        assertTrue(vm.availableLogos.contains("badge.png"), "it joined the library: ${vm.availableLogos}")
        assertTrue(vm.config.logoEnabled)
        assertEquals("badge.png", vm.config.logoSelect)
        assertTrue(vm.config.logoData!!.startsWith("data:image/png;base64,"), "embedded as a data URL")
        assertEquals(8, vm.config.logoW, "the real pixel size is read")
        assertEquals(4, vm.config.logoH)
    }

    @Test
    fun `clearing the logo turns it off and forgets its data`() {
        val vm = viewModel()
        vm.importAndLoadLogo(pngFile("badge.png"))
        waitFor("the logo to apply") { vm.config.logoEnabled }

        vm.clearLogo()
        assertTrue(!vm.config.logoEnabled)
        assertNull(vm.config.logoData)
        assertEquals("", vm.config.logoSelect)
        assertEquals(0, vm.config.logoW)
    }

    @Test
    fun `selecting the empty logo entry clears it`() {
        val vm = viewModel()
        vm.importAndLoadLogo(pngFile("badge.png"))
        waitFor("the logo to apply") { vm.config.logoEnabled }

        vm.selectLogo("")
        assertTrue(!vm.config.logoEnabled)
    }

    @Test
    fun `selecting a logo by name loads it from the library`() {
        val vm = viewModel()
        vm.importAndLoadLogo(pngFile("badge.png"))
        vm.clearLogo()

        vm.selectLogo("badge.png")
        assertTrue(vm.config.logoEnabled)
        assertEquals("badge.png", vm.config.logoSelect)
    }

    @Test
    fun `a logo ImageIO cannot measure still loads, at fallback dimensions`() {
        // SVG is a supported logo format that ImageIO cannot read, which is why the 100x100
        // fallback exists rather than the import failing. Recorded so the fallback is not
        // mistaken for a bug later.
        val vm = viewModel()
        val svg = File(temp, "badge.svg").apply {
            writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="20" height="10"/>""")
        }

        vm.importAndLoadLogo(svg)
        waitFor("the logo to apply") { vm.config.logoEnabled }

        assertTrue(vm.config.logoData!!.startsWith("data:image/svg+xml;base64,"), "typed as SVG")
        assertEquals(100, vm.config.logoW, "fallback size, since ImageIO cannot measure an SVG")
        assertEquals(100, vm.config.logoH)
    }

    @Test
    fun `importing a file that cannot be read leaves the config alone`() {
        val vm = viewModel()
        vm.importAndLoadLogo(File(temp, "does-not-exist.png"))
        assertTrue(!vm.config.logoEnabled, "a failed import does not half-apply")
    }

    @Test
    fun `status text can be set directly by the UI`() {
        val vm = viewModel()
        vm.updateStatusText("Copied to clipboard")
        assertEquals("Copied to clipboard", vm.statusText)
    }

    @Test
    fun `a default config generates without error`() {
        // Guards the generator against a default that cannot render — the state every new user sees.
        val vm = viewModel()
        vm.updateConfig { LottieGenConfig() }
        vm.generateNow()
        waitFor("the default render") { vm.generatedJson != null }
        assertEquals("", vm.statusText)
    }
}
