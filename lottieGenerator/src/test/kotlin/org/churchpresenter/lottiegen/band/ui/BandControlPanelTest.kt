@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.lottiegen.band.BandColorRole
import org.churchpresenter.lottiegen.band.BandEntrance
import org.churchpresenter.lottiegen.band.BandStyle
import org.churchpresenter.lottiegen.band.BandTextAlign
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.band.ReferencePlacement
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.lottiegen.lottie.TextShaping
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.choose
import org.churchpresenter.lottiegen.ui.components.FakePickers
import org.churchpresenter.lottiegen.ui.components.LocalLottieGenPickers
import org.churchpresenter.lottiegen.ui.click
import org.churchpresenter.lottiegen.ui.clickDescription
import org.churchpresenter.lottiegen.ui.fillEveryField
import org.churchpresenter.lottiegen.ui.hasNode
import org.churchpresenter.lottiegen.ui.pick
import org.churchpresenter.lottiegen.ui.rebindTick
import org.churchpresenter.lottiegen.ui.showDark
import org.churchpresenter.lottiegen.ui.tapBelow
import org.churchpresenter.lottiegen.ui.tapRightOf
import org.churchpresenter.lottiegen.ui.type
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BandControlPanelTest {

    private lateinit var temp: File
    private lateinit var savedHome: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("band-panel-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        scope.cancel()
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    private fun viewModel(
        seed: BibleLottieGenConfig = BibleLottieGenConfig(),
        outputDir: File? = null,
        onSaved: ((File) -> Unit)? = null,
    ) = BibleLottieGenViewModel(scope, outputDir, onSaved, seed)

    private val kind = "bible"

    @Test
    fun `the band pane picks a template and edits its colours and measures`() = runDesktopComposeUiTest(700, 1600) {
        val vm = viewModel()
        showDark { val tick = rebindTick(); BandControlPanel(vm, 460.dp, pickImage = remember(tick) { { null } }) }
        click(Strings.bandTemplate.uppercase())
        pick(Strings.bandEnumLabel("style", BandStyle.GRADIENT_TRIO.name))
        assertEquals(BandStyle.GRADIENT_TRIO, vm.config.bandStyle)
        assertTrue(hasNode(Strings.bandColorThird.uppercase()))
        fillEveryField("123456")
        val c = vm.config
        assertEquals(List(4) { "#123456" }, listOf(c.bgColor, c.gradientColor, c.accentColor, c.tertiaryColor))
        listOf(
            Strings.bandGradientPosition, Strings.bandBorderThickness, Strings.bandCornerRadius,
            Strings.bandInset, Strings.bandPadding,
        )
            .forEach { tapBelow(it, 13.dp, 0.9f) }
        val m = vm.config
        assertTrue(m.borderThickness > 0 && m.cornerRadiusPx > 0 && m.insetPx > 0 && m.paddingPx > 0)
        assertNotEquals(BibleLottieGenConfig().gradientPosition, m.gradientPosition)
        assertTrue(hasNode(Strings.bandBorderColor.uppercase()))
        repeat(5) { tapRightOf(Strings.bandLookAlpha, 30.dp, it) }
        val a = vm.config
        assertTrue(listOf(a.bgAlpha, a.secondAlpha, a.accentAlpha, a.tertiaryAlpha, a.borderAlpha).all { it < 100 })
    }

    @Test
    fun `the look popover washes a role and stands a picture in for it`() = runDesktopComposeUiTest(700, 1600) {
        val picture = File(temp, "wood.png")
        ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", picture)
        val vm = viewModel()
        showDark { val tick = rebindTick(); BandControlPanel(vm, 460.dp, pickImage = remember(tick) { { picture } }) }
        clickDescription(Strings.bandLookTooltip)
        assertTrue(hasNode(Strings.bandLookWash.uppercase()) || hasNode(Strings.bandLookWash))
        click(Strings.bandImageChoose)
        waitUntil(timeoutMillis = 5_000) { vm.config.images.containsKey(BandColorRole.BACKGROUND) }
        waitForIdle()
        assertTrue(hasNode("wood.png"))
        listOf(
            Strings.bandLookBlur, Strings.bandLookOffsetX, Strings.bandLookOffsetY,
            Strings.bandLookScale, Strings.bandLookRotation,
        )
            .forEach { tapRightOf(it, 60.dp) }
        val image = vm.config.images.getValue(BandColorRole.BACKGROUND)
        assertTrue(vm.config.look(BandColorRole.BACKGROUND).blurPx > 0)
        assertNotEquals(0, image.offsetXPercent)
        assertNotEquals(100, image.scalePercent)
        clickDescription(Strings.bandLookReset)
        val reset = vm.config.images.getValue(BandColorRole.BACKGROUND)
        assertEquals(
            listOf(0, 0, 100, 0),
            listOf(reset.offsetXPercent, reset.offsetYPercent, reset.scalePercent, reset.rotationDegrees),
        )
        clickDescription(Strings.bandImageClear)
        assertNull(vm.config.images[BandColorRole.BACKGROUND])
        click(Strings.ok)
        assertTrue(!hasNode(Strings.bandImageChoose))
    }

    @Test
    fun `without a host chooser the popover asks the generator own chooser for a picture`() =
        runDesktopComposeUiTest(700, 1600) {
        val picture = File(temp, "stone.png")
        ImageIO.write(BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", picture)
        val pickers = FakePickers(file = picture)
        val vm = viewModel()
        showDark { CompositionLocalProvider(LocalLottieGenPickers provides pickers) { BandControlPanel(vm, 460.dp) } }
        clickDescription(Strings.bandLookTooltip)
        click(Strings.bandImageChoose)
        waitUntil(timeoutMillis = 5_000) { vm.config.images.containsKey(BandColorRole.BACKGROUND) }
        assertEquals("stone.png", vm.config.images.getValue(BandColorRole.BACKGROUND).name)
        assertEquals(listOf("file ${Strings.bandImage} png,jpg,jpeg,webp"), pickers.asked)
    }

    @Test
    fun `the layout pane sets the pickers, the text area and the guides`() = runDesktopComposeUiTest(700, 1600) {
        val vm = viewModel()
        showDark { val tick = rebindTick(); BandControlPanel(vm, 460.dp, pickImage = remember(tick) { { null } }) }
        click(Strings.bandSectionLayout)
        choose(Strings.bandLayout, Strings.bandEnumLabel("layout", SlotLayout.GRID_2X2.name))
        val above = Strings.bandLabel("reference_${ReferencePlacement.ABOVE.name.lowercase()}", kind)
        choose(Strings.bandLabel("reference", kind), above)
        choose(Strings.bandLabel("text_align", kind), Strings.bandEnumLabel("align", BandTextAlign.RIGHT.name))
        choose(Strings.bandLabel("reference_align", kind), Strings.bandLabel("align_follow_settings", kind))
        val c = vm.config
        assertEquals(SlotLayout.GRID_2X2, c.layout)
        assertEquals(ReferencePlacement.ABOVE, c.referencePlacement)
        assertEquals(BandTextAlign.RIGHT to BandTextAlign.FOLLOW_SETTINGS, c.textAlign to c.referenceAlign)
        listOf(Strings.bandTextAreaLeft, Strings.bandTextAreaRight, Strings.bandTextAreaTop, Strings.bandTextAreaBottom)
            .forEach { tapBelow(it, 13.dp, 0.9f) }
        assertTrue(vm.config.textAreaLeftPx > 0 && vm.config.textAreaBottomPx > 0)
        click(Strings.bandTextAreaLink)
        assertTrue(vm.linkTextArea)
        tapBelow(Strings.bandTextAreaLeft, 13.dp, 0.1f)
        val linked = vm.config
        assertEquals(
            1,
            setOf(linked.textAreaLeftPx, linked.textAreaRightPx, linked.textAreaTopPx, linked.textAreaBottomPx).size,
        )
        tapBelow(Strings.bandLabel("reference_height", kind), 13.dp, 0.9f)
        assertTrue(vm.config.referenceHeightFraction > 0.4f)
        click(Strings.bandShowSlotGuides)
        assertEquals(false, vm.showSlotGuides)
    }

    @Test
    fun `the motion pane picks the movements and times each phase`() = runDesktopComposeUiTest(700, 1600) {
        val vm = viewModel()
        showDark { val tick = rebindTick(); BandControlPanel(vm, 460.dp, pickImage = remember(tick) { { null } }) }
        click(Strings.bandTabMotion)
        choose(Strings.bandEntrance, Strings.bandEnumLabel("entrance", BandEntrance.GROW.name))
        choose(Strings.bandTextAnimation, Strings.bandEnumLabel("text", TextAnimation.TICKER.name))
        assertEquals(BandEntrance.GROW to TextAnimation.TICKER, vm.config.entrance to vm.config.textAnimation)
        tapBelow(Strings.bandTickerSpeed, 13.dp, 0.9f)
        assertTrue(vm.config.tickerPxPerSecond > 400)
        val before = vm.config
        BandPhase.entries.forEach { tapRightOf(it.label, 200.dp) }
        tapRightOf(Strings.bandTimeCrossfade, 200.dp)
        val after = vm.config
        BandPhase.entries.forEach { assertNotEquals(it.seconds(before), it.seconds(after), it.name) }
        assertNotEquals(before.swapSeconds, after.swapSeconds)
    }

    @Test
    fun `the text pane styles the sample and edits each language`() = runDesktopComposeUiTest(700, 1600) {
        val vm = viewModel(BibleLottieGenConfig(layout = SlotLayout.GRID_2X2))
        showDark { val tick = rebindTick(); BandControlPanel(vm, 460.dp, pickImage = remember(tick) { { null } }) }
        click(Strings.bandTabText)
        choose(Strings.bandPreviewFont, "Poppins")
        // The style keys are checkboxes named for what they do; the letter on each is drawn, not read.
        clickDescription(Strings.bandPreviewBold)
        clickDescription(Strings.bandPreviewItalic)
        clickDescription(Strings.bandPreviewShadow)
        choose(Strings.textShaping, Strings.bandEnumLabel("shaping", TextShaping.WHOLE_LINES.key))
        val c = vm.config
        assertEquals("Poppins", c.previewFontFamily)
        assertTrue(c.previewBold && c.previewItalic && c.previewShadow)
        assertEquals(TextShaping.WHOLE_LINES, c.textShaping)
        tapBelow(Strings.bandLabel("text_opacity", kind), 13.dp, 0.1f)
        tapBelow(Strings.bandLabel("reference_opacity", kind), 13.dp, 0.1f)
        assertTrue(vm.config.textAlpha < 50 && vm.config.referenceAlpha < 50)
        for (i in 1..4) {
            click(Strings.bandLabel("preview_text_$i", kind))
            type(previewText(vm.config, i), "Verse $i")
            type(previewReference(vm.config, i), "Ref $i")
        }
        val t = vm.config
        assertEquals(
            listOf("Verse 1", "Verse 2", "Verse 3", "Verse 4"),
            listOf(t.previewText1, t.previewText2, t.previewText3, t.previewText4),
        )
        assertEquals(listOf("Ref 1", "Ref 2", "Ref 3", "Ref 4"),
            listOf(t.previewReference1, t.previewReference2, t.previewReference3, t.previewReference4))
        type(t.previewTextColor, "abcdef")
        type(t.previewReferenceColor, "#fedcba")
        assertEquals("#ABCDEF" to "#FEDCBA", vm.config.previewTextColor to vm.config.previewReferenceColor)
    }

    @Test
    fun `the save pane names the file and writes it`() = runDesktopComposeUiTest(700, 1600) {
        val out = File(temp, "out")
        var saved: File? = null
        val vm = viewModel(outputDir = out, onSaved = { saved = it })
        showDark { val tick = rebindTick(); BandControlPanel(vm, 460.dp, pickImage = remember(tick) { { null } }) }
        click(Strings.bandSectionSave)
        assertTrue(hasNode(Strings.bandSummaryTemplate) && hasNode(Strings.bandSummaryDuration))
        type(vm.fileName, "my band")
        assertEquals("my band", vm.fileName)
        waitUntil(timeoutMillis = 5_000) { vm.generatedJson != null }
        click(Strings.bandSave)
        assertEquals("my band.json", assertNotNull(saved).name)
        assertTrue(hasNode(Strings.bandSaved))
    }

    @Test
    fun `the template menu walks the styles with the arrow keys and closes on escape`() =
        runDesktopComposeUiTest(700, 1600) {
        val vm = viewModel()
        showDark { BandControlPanel(vm, 460.dp) }
        click(Strings.bandTemplate.uppercase())
        val menu = onNode(isPopup())
        menu.performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        menu.performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(BandStyle.entries[2], vm.config.bandStyle)
        menu.performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(BandStyle.entries[1], vm.config.bandStyle)
        menu.performKeyInput { pressKey(Key.A) }
        menu.performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertTrue(onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty())
        click(Strings.bandTemplate.uppercase())
        onNode(isPopup()).performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionUp); pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(BandStyle.entries[0], vm.config.bandStyle)
    }

    @Test
    fun `the look popover stays up while a picture is being chosen`() = runDesktopComposeUiTest(700, 1600) {
        val release = CompletableDeferred<File?>()
        val vm = viewModel()
        showDark { BandControlPanel(vm, 460.dp, pickImage = { release.await() }) }
        clickDescription(Strings.bandLookTooltip)
        click(Strings.bandImageChoose)
        assertTrue(vm.choosingImage)
        click(Strings.bandTemplate.uppercase())
        assertTrue(hasNode(Strings.bandImageChoose))
        release.complete(File(temp, "missing.png"))
        waitUntil(timeoutMillis = 5_000) { !vm.choosingImage }
        waitForIdle()
        assertEquals(Strings.bandStatusPictureUnreadable("missing.png"), vm.statusText)
        assertTrue(vm.config.images.isEmpty())
    }

    @Test
    fun `a host font picker replaces the bundled list`() = runDesktopComposeUiTest(700, 1600) {
        val vm = viewModel()
        showDark {
            BandControlPanel(
                vm, 460.dp,
                fontPicker = { family, onPick, _ -> Text("host:$family", Modifier.clickable { onPick("Lora") }) },
            )
        }
        click(Strings.bandTabText)
        click("host:" + vm.config.previewFontFamily)
        assertEquals("Lora", vm.config.previewFontFamily)
    }

    private fun previewText(c: BibleLottieGenConfig, i: Int) =
        listOf(c.previewText1, c.previewText2, c.previewText3, c.previewText4)[i - 1]

    private fun previewReference(c: BibleLottieGenConfig, i: Int) =
        listOf(c.previewReference1, c.previewReference2, c.previewReference3, c.previewReference4)[i - 1]
}
