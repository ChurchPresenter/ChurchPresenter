package org.churchpresenter.lottiegen.band

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.lottiegen.ui.Strings
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleLottieGenViewModelTest {

    private lateinit var temp: File
    private lateinit var savedHome: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("band-vm-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        scope.cancel()
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    private fun waitFor(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(2)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun viewModel(outputDir: File? = null, onSaved: ((File) -> Unit)? = null): BibleLottieGenViewModel {
        val vm = BibleLottieGenViewModel(scope, outputDir, onSaved, BibleLottieGenConfig(canvasW = 640, canvasH = 120))
        waitFor("the first render") { vm.generatedJson != null }
        return vm
    }

    @Test
    fun `construction renders the seed`() {
        val vm = viewModel()
        assertTrue(assertNotNull(vm.generatedJson).contains("\"markers\""))
        assertEquals(640, vm.config.canvasW)
        assertEquals("", vm.statusText)
        assertEquals(BandTimeline.from(vm.config), vm.timeline)
    }

    @Test
    fun `changing the config regenerates and renames the suggested file`() {
        val vm = viewModel()
        val before = vm.generatedJson
        assertEquals("bible-band-solid_bar-slide_up-fade", vm.fileName)
        vm.updateConfig { it.copy(bandStyle = BandStyle.WAVE_DECK, entrance = BandEntrance.FADE) }
        waitFor("a new render") { vm.generatedJson != before }
        assertEquals("bible-band-wave_deck-fade-fade", vm.fileName)
    }

    @Test
    fun `a file name the user typed is kept when the design changes`() {
        val vm = viewModel()
        vm.updateFileName("sunday")
        vm.updateConfig { it.copy(entrance = BandEntrance.GROW) }
        assertEquals("sunday", vm.fileName)
    }

    @Test
    fun `save writes the JSON, reports it, remembers the design and hands the file back`() {
        val out = File(temp, "out")
        var saved: File? = null
        val vm = viewModel(out) { saved = it }
        vm.updateFileName("my band")
        val file = assertNotNull(vm.save())
        assertEquals(File(out, "my band.json"), file)
        assertEquals(file, saved)
        assertTrue(file.readText().contains("\"layers\""))
        assertEquals(Strings.bandStatusSaved("my band.json"), vm.statusText)
        assertEquals(vm.config, BandConfigStorage.load())
    }

    @Test
    fun `unsafe characters in the name are replaced and an empty name gets the default`() {
        val out = File(temp, "out")
        val vm = viewModel(out)
        vm.updateFileName("a/b:c")
        assertEquals("a_b_c.json", assertNotNull(vm.save()).name)
        vm.updateFileName("   ")
        assertEquals("bible-band.json", assertNotNull(vm.save()).name)
    }

    @Test
    fun `save without an output folder does nothing`() {
        assertNull(viewModel(outputDir = null).save())
    }

    @Test
    fun `a picture is loaded into its role and a first background picture lowers the fill to a tint`() {
        val vm = viewModel()
        val png = File(temp, "p.png").also { ImageIO.write(BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB), "png", it) }
        vm.loadBandImage(BandColorRole.BACKGROUND, png)
        val image = assertNotNull(vm.config.images[BandColorRole.BACKGROUND])
        assertEquals("p.png", image.name)
        assertEquals(4 to 2, image.width to image.height)
        assertEquals(40, vm.config.bgAlpha)
        vm.updateConfig { it.copy(bgAlpha = 75) }
        vm.loadBandImage(BandColorRole.BACKGROUND, png)
        assertEquals(75, vm.config.bgAlpha, "an alpha the user set stays")
        vm.loadBandImage(BandColorRole.ACCENT, png)
        assertEquals(75, vm.config.bgAlpha, "another role's picture does not touch the fill")
        vm.clearBandImage(BandColorRole.BACKGROUND)
        assertNull(vm.config.images[BandColorRole.BACKGROUND])
        assertNotNull(vm.config.images[BandColorRole.ACCENT])
    }

    @Test
    fun `a picture that cannot be read is reported and the config left alone`() {
        val vm = viewModel()
        vm.loadBandImage(BandColorRole.SECOND, File(temp, "nope.png"))
        assertEquals(Strings.bandStatusPictureUnreadable("nope.png"), vm.statusText)
        assertTrue(vm.config.images.isEmpty())
    }

    @Test
    fun `every band label the panel asks for exists in the bundle, for both kinds`() {
        for (kind in BandContentKind.entries) {
            val k = kind.name.lowercase()
            listOf(
                "reference", "reference_above", "reference_below", "reference_height", "text_align", "reference_align",
                "align_follow_settings", "preview_text_size", "preview_reference_size",
                "preview_text_1", "preview_reference_1", "preview_text_2", "preview_reference_2",
            )
                .forEach { assertTrue(Strings.bandLabel(it, k).isNotBlank(), "$it for $k") }
        }
        BandStyle.entries.forEach { assertTrue(Strings.bandEnumLabel("style", it.name).isNotBlank()) }
        BandEntrance.entries.forEach { assertTrue(Strings.bandEnumLabel("entrance", it.name).isNotBlank()) }
        TextAnimation.entries.forEach { assertTrue(Strings.bandEnumLabel("text", it.name).isNotBlank()) }
        SlotLayout.entries.forEach { assertTrue(Strings.bandEnumLabel("layout", it.name).isNotBlank()) }
        BandTextAlign.entries.filter { it != BandTextAlign.FOLLOW_SETTINGS }
            .forEach { assertTrue(Strings.bandEnumLabel("align", it.name).isNotBlank()) }
    }
}
