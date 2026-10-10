@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.model.CANVAS_PRESETS
import org.churchpresenter.lottiegen.model.ColorTheme
import org.churchpresenter.lottiegen.model.ColorThemeColors
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.Preset
import org.churchpresenter.lottiegen.model.StyleCatalog
import org.churchpresenter.lottiegen.model.TIMING_PRESETS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlPanelTest {

    private val theme = ColorTheme("Mine", ColorThemeColors("#FFFFFF", "#000000", "#FF0000", "#00FF00", "#0000FF"))
    private val preset = Preset("Pastor", "2026-01-01", LottieGenConfig(nameText = "Pastor"))

    private fun openAll(test: androidx.compose.ui.test.ComposeUiTest) = with(test) {
        listOf(
            Strings.sectionCanvas, Strings.sectionTextStyle, Strings.sectionShape,
            Strings.sectionLogo, Strings.sectionTiming, Strings.sectionPosition,
        ).forEach { click(it) }
    }

    @Test
    fun `canvas, timing and text fields write the config`() = runDesktopComposeUiTest(1400, 2600) {
        val state = FakeLottieGenState()
        showDark { ControlPanel(state, 600.dp) }
        openAll(this)
        click(CANVAS_PRESETS[4].label)
        assertEquals(1080 to 1080, state.config.canvasW to state.config.canvasH)
        type("1080", "99999")
        assertEquals(7680, state.config.canvasW)
        type("1080", "12")
        assertTrue(state.config.canvasH < 1080)
        click(TIMING_PRESETS[0].label)
        assertEquals(2f, state.config.animDuration)
        type("2.0", "100")
        type(state.config.holdDuration.toString(), "7")
        assertEquals(7f, state.config.holdDuration)
        type("Church Presenter", "Ann")
        type("Software Engineer", "Elder")
        assertEquals("Ann" to "Elder", state.config.nameText to state.config.infoText)
        type(state.config.baseSize.toString(), "30")
        type(state.config.nameSize.toString(), "2")
        type(state.config.infoSize.toString(), "1.5")
        type(state.config.detailSize.toString(), "1.25")
        assertEquals(30, state.config.baseSize)
        assertEquals(listOf(2f, 1.5f, 1.25f), listOf(state.config.nameSize, state.config.infoSize, state.config.detailSize))
    }

    @Test
    fun `checkboxes and dropdowns change the look`() = runDesktopComposeUiTest(1400, 2600) {
        val state = FakeLottieGenState(availableLogos = listOf("church.png"))
        showDark { ControlPanel(state, 600.dp) }
        openAll(this)
        listOf(Strings.showBackground, Strings.shadow, Strings.hideName, Strings.hideInfo, Strings.hideDetail)
            .forEach { click(it) }
        val c = state.config
        assertEquals(listOf(false, true, true, true, false), listOf(c.bgEnabled, c.shadowEnabled, c.hideName, c.hideInfo, c.hideDetail))
        click(Strings.showLogo)
        assertTrue(state.config.logoEnabled)

        choose(Strings.alignment, Strings.alignRight)
        assertEquals("right", state.config.align)
        choose(Strings.nameWeight, Strings.normal)
        choose(Strings.infoWeight, Strings.bold)
        choose(Strings.detailWeight, Strings.bold)
        assertEquals(listOf(400, 700, 700), listOf(state.config.nameWeight, state.config.infoWeight, state.config.detailWeight))
        choose(Strings.nameWeight, Strings.bold)
        choose(Strings.infoWeight, Strings.normal)
        choose(Strings.detailWeight, Strings.normal)
        choose(Strings.nameTransform, Strings.none)
        choose(Strings.infoTransform, Strings.uppercase)
        choose(Strings.detailTransform, Strings.uppercase)
        assertEquals(listOf("none", "uppercase", "uppercase"),
            listOf(state.config.nameTransform, state.config.infoTransform, state.config.detailTransform))
        choose(Strings.nameTransform, Strings.uppercase)
        choose(Strings.infoTransform, Strings.none)
        choose(Strings.detailTransform, Strings.none)
        choose(Strings.textShaping, Strings.bandEnumLabel("shaping", "letters"))
        assertEquals("letters", state.config.textShaping)
        choose(Strings.font, "Poppins")
        assertEquals("Poppins", state.config.fontFamily)

        choose(Strings.logoLabel, "church.png")
        choose(Strings.logoLabel, Strings.logoNone)
        assertEquals(listOf("selectLogo church.png", "clearLogo"), state.calls.filter { "Logo" in it })

        val target = StyleCatalog.entries.last()
        choose(Strings.style, target.label)
        assertEquals(target.id, state.config.style)
        assertTrue("thumbnails" in state.calls)
    }

    @Test
    fun `sliders under their labels move their values`() = runDesktopComposeUiTest(1400, 2600) {
        val state = FakeLottieGenState()
        showDark { ControlPanel(state, 600.dp) }
        openAll(this)
        val before = state.config
        listOf(Strings.scale, Strings.corners, Strings.borderThickness, Strings.logoSize,
            Strings.marginH, Strings.marginV, Strings.lineSpacing).forEach { tapBelow(it, 17.dp, 0.9f) }
        val after = state.config
        assertTrue(after.scaleFactor > before.scaleFactor)
        assertTrue(after.corners > before.corners)
        assertTrue(after.borderThickness > before.borderThickness)
        assertTrue(after.marginH > before.marginH && after.marginV > before.marginV)
        assertTrue(after.lineSpacing > before.lineSpacing)
        assertTrue(after.logoSize > before.logoSize)
    }

    @Test
    fun `library and colour themes route to the state`() = runDesktopComposeUiTest(1400, 2600) {
        val state = FakeLottieGenState(hasOutputDir = true, colorThemes = listOf(theme), presets = listOf(preset))
        showDark { ControlPanel(state, 600.dp) }
        click(Strings.saveColors)
        click("Mine")
        clickDescription("Delete")
        click("Pastor")
        click(Strings.applyStyleToAll)
        click(Strings.saveAllLowerThirds)
        click(Strings.saveLowerThird)
        assertEquals(
            listOf("saveTheme", "loadTheme 0", "deleteTheme 0", "loadPreset 0", "applyAll", "batchDownload null", "saveLowerThird"),
            state.calls.filterNot { it == "thumbnails" },
        )
    }

    @Test
    fun `without an output folder the library offers the library save and no presets line`() = runDesktopComposeUiTest(1400, 2600) {
        val state = FakeLottieGenState(
            initial = LottieGenConfig(hideName = true, hideInfo = true, hideDetail = true, detailText = "x"),
        )
        showDark { ControlPanel(state, 600.dp) }
        assertTrue(hasNode(Strings.noPresets))
        assertFalse(hasNode(Strings.saveLowerThird))
        click(Strings.saveToLibrary)
        assertEquals(listOf("savePreset"), state.calls.filterNot { it == "thumbnails" })
    }

    @Test
    fun `batch import reads the pasted text and reports the count`() = runDesktopComposeUiTest(1400, 2600) {
        val state = FakeLottieGenState()
        showDark { ControlPanel(state, 600.dp) }
        click(Strings.batchImport)
        assertTrue(hasNode(Strings.batchImportTitle))
        typeLast("", "Ann | Elder")
        click(Strings.batchImportBtn)
        assertEquals("batch Ann | Elder", state.calls.last())
        assertEquals(Strings.batchImportedStatus(2, 1), state.status)
        click(Strings.batchImport)
        click(Strings.cancelBtn)
        assertFalse(hasNode(Strings.batchImportTitle))
    }

    @Test
    fun `the light theme and the scheme palette render the panel`() = runDesktopComposeUiTest(1400, 2600) {
        val state = FakeLottieGenState()
        setContent {
            LottieGenTheme(dark = false) {
                ProvideLottieGenPalette(paletteFrom(androidx.compose.material3.MaterialTheme.colorScheme)) {
                    ControlPanel(state, 500.dp)
                    Text("probe")
                }
            }
        }
        waitForIdle()
        assertTrue(hasNode(Strings.appTitle))
    }
}
