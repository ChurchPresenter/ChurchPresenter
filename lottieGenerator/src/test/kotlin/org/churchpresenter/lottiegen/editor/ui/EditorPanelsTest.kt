@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.editor.ExportIssue
import org.churchpresenter.lottiegen.editor.MatrixCell
import org.churchpresenter.lottiegen.editor.RegisterResult
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.CANVAS_PRESETS
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.persistence.StyleSpecStorage
import org.churchpresenter.lottiegen.spec.AnimProperty
import org.churchpresenter.lottiegen.spec.AnimTrack
import org.churchpresenter.lottiegen.spec.ColorRole
import org.churchpresenter.lottiegen.spec.RectElement
import org.churchpresenter.lottiegen.spec.SpecKeyframe
import org.churchpresenter.lottiegen.spec.StyleSpec
import org.churchpresenter.lottiegen.ui.EditorStrings
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.choose
import org.churchpresenter.lottiegen.ui.click
import org.churchpresenter.lottiegen.ui.clickDescription
import org.churchpresenter.lottiegen.ui.hasNode
import org.churchpresenter.lottiegen.ui.node
import org.churchpresenter.lottiegen.ui.pick
import org.churchpresenter.lottiegen.ui.showDark
import org.churchpresenter.lottiegen.ui.tapBelow
import org.churchpresenter.lottiegen.ui.type
import org.churchpresenter.lottiegen.ui.typeLast
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorPanelsTest {

    private lateinit var temp: File
    private lateinit var savedHome: String

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("editor-panels-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    private fun lottieJson(config: LottieGenConfig = LottieGenConfig()): String =
        Json.encodeToString(JsonObject.serializer(), LottieGenerator.generate(config))

    @Test
    fun `the test config panel edits the sample config`() = runDesktopComposeUiTest(900, 1600) {
        val logos = File(temp, ".churchpresenter/churchpresenter-lottiegen/logos").apply { mkdirs() }
        ImageIO.write(BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB), "png", File(logos, "mark.png"))
        val state = FakeEditorState()
        showDark { TestConfigPanel(state) }
        click(Strings.editorTestConfig)
        choose(Strings.editorTestAlignment, EditorLabels.align("center"))
        type("Church Presenter", "Ann")
        type("Software Engineer", "Elder")
        click(Strings.editorRuleName)
        click(Strings.editorRuleInfo)
        click(Strings.editorRuleBg)
        choose(Strings.editorTestLogo, "mark.png")
        val cfg = state.testConfig
        assertEquals("center", cfg.align)
        assertEquals("Ann" to "Elder", cfg.nameText to cfg.infoText)
        assertEquals(listOf(true, true, false), listOf(cfg.hideName, cfg.hideInfo, cfg.bgEnabled))
        assertEquals(Triple(true, "mark.png", 8), Triple(cfg.logoEnabled, cfg.logoSelect, cfg.logoW))
        choose(Strings.editorTestLogo, Strings.editorNone)
        assertFalse(state.testConfig.logoEnabled)
        val square = CANVAS_PRESETS[4]
        choose(Strings.editorTestCanvas, "${square.label} (${square.width}×${square.height})")
        assertEquals(1080 to 1080, state.testConfig.canvasW to state.testConfig.canvasH)
        listOf(Strings.editorTestBorder, Strings.editorTestAnimDuration, Strings.editorTestHoldDuration)
            .forEach { tapBelow(it, 17.dp, 0.9f) }
        assertTrue(state.testConfig.borderThickness > 3f)
        assertTrue(state.testConfig.animDuration > 7f && state.testConfig.holdDuration > 7f)
    }

    @Test
    fun `each colour role's alpha slider writes its own channel`() = runDesktopComposeUiTest(900, 1600) {
        val state = FakeEditorState()
        showDark { TestConfigPanel(state) }
        click(Strings.editorTestConfig)
        for (role in ColorRole.entries) {
            node(EditorLabels.role(role)).performTouchInput {
                val touch = this
                touch.click(Offset(width + 300.dp.toPx(), height / 2f))
            }
            waitForIdle()
        }
        val c = state.testConfig
        val alphas = listOf(c.nameColorAlpha, c.infoColorAlpha, c.detailColorAlpha, c.accentColorAlpha, c.bgColorAlpha, c.borderColorAlpha)
        assertTrue(alphas.all { it < 100 }, "alphas $alphas")
    }

    @Test
    fun `a colour row opens the picker and its hex lands in the config`() = runDesktopComposeUiTest(900, 1600) {
        val state = FakeEditorState()
        showDark { TestConfigPanel(state) }
        click(Strings.editorTestConfig)
        val roles = ColorRole.entries
        val hexes = listOf("#112233", "#223344", "#334455", "#445566", "#556677", "#667788")
        roles.forEachIndexed { i, role ->
            val current = when (role) {
                ColorRole.NAME -> state.testConfig.nameColor
                ColorRole.INFO -> state.testConfig.infoColor
                ColorRole.DETAIL -> state.testConfig.detailColor
                ColorRole.ACCENT -> state.testConfig.accentColor
                ColorRole.BG -> state.testConfig.bgColor
                ColorRole.BORDER -> state.testConfig.borderColor
            }
            click(current.uppercase())
            assertTrue(hasNode(Strings.chooseColor))
            typeLast(current.uppercase(), hexes[i])
            click(Strings.ok)
        }
        val c = state.testConfig
        assertEquals(hexes, listOf(c.nameColor, c.infoColor, c.detailColor, c.accentColor, c.bgColor, c.borderColor))
    }

    @Test
    fun `the timeline selects an element on its keyframe and scrubs elsewhere`() = runDesktopComposeUiTest(900, 400) {
        val tracks = AnimProperty.entries.map { AnimTrack(it, listOf(SpecKeyframe(50.0, listOf(1.0, 1.0)))) }
        val state = FakeEditorState(StyleSpec(elements = listOf(RectElement("r", name = "Bar", tracks = tracks))))
        var seek = 0f
        var playing = true
        showDark { TimelinePanel(state, seek, { seek = it }, { playing = it }) }
        assertTrue(hasNode(Strings.editorTimeline))
        node("Bar").performTouchInput {
            val touch = this
            touch.click(Offset(110.dp.toPx() + (900 - 32 - 110).dp.toPx() / 2, height / 2f))
        }
        waitForIdle()
        assertEquals("r", state.selectedElementId)
        node("Bar").performTouchInput {
            val touch = this
            touch.click(Offset(110.dp.toPx() + 10.dp.toPx(), height / 2f))
        }
        waitForIdle()
        assertFalse(playing)
        assertTrue(seek in 0f..0.25f)
    }

    @Test
    fun `the matrix waits for cells, then labels each one`() = runDesktopComposeUiTest(1200, 1200) {
        val state = FakeEditorState()
        showDark { TestMatrixView(state) }
        assertTrue(hasNode(Strings.generating))
        val json = lottieJson()
        state.matrixCells = listOf("left", "center", "right").flatMap { align ->
            listOf(MatrixCell(align, logo = true, bg = true, json = json), MatrixCell(align, logo = false, bg = false, json = json))
        }
        waitForIdle()
        assertTrue(hasNode("${EditorLabels.align("left")} · ${Strings.editorMatrixTagLogo} · ${Strings.editorMatrixTagBg}"))
        assertTrue(hasNode(EditorLabels.align("right")))
    }

    @Test
    fun `the preview plays, pauses and seeks`() = runDesktopComposeUiTest(900, 700) {
        var playing by mutableStateOf(true)
        var seek by mutableStateOf(0f)
        var json by mutableStateOf<String?>(null)
        showDark {
            Column {
                EditorPreview(json, 16f / 9f, playing, seek, { playing = it }, { seek = it }, Modifier.fillMaxWidth().height(500.dp))
            }
        }
        assertTrue(hasNode(Strings.generating))
        json = lottieJson()
        waitForIdle()
        clickDescription(Strings.editorPause)
        assertFalse(playing)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).onFirst()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        waitForIdle()
        assertEquals(0.5f, seek)
        assertTrue(hasNode("50%"))
        clickDescription(Strings.editorPlay)
        assertTrue(playing)
    }

    @Test
    fun `the toolbar opens new, open, save as and export, and confirms discarding`() = runDesktopComposeUiTest(1400, 1200) {
        val specs = File(temp, ".churchpresenter/churchpresenter-lottiegen/style-specs").apply { mkdirs() }
        StyleSpecStorage.save(StyleSpec(id = "91", name = "Kept"), File(specs, "kept.json"))
        StyleSpecStorage.save(StyleSpec(id = "92", name = "Gone"), File(specs, "gone.json"))
        val state = FakeEditorState()
        showDark { ProjectToolbarActions(state) }

        click(Strings.editorNew)
        click(Strings.editorNewBlank)
        click(Strings.editorNew)
        pick(Strings.byKey("editor_new_from_vine"))
        assertEquals(listOf("new null", "new ${EditorViewModel_VINE}"), state.calls)

        state.dirty = true
        waitForIdle()
        click(Strings.editorNew)
        assertTrue(hasNode(Strings.editorUnsavedTitle))
        click(Strings.cancelBtn)
        click(Strings.editorNew)
        click(Strings.editorDiscard)
        assertTrue(hasNode(Strings.editorNewTitle))
        click(Strings.cancelBtn)

        click(Strings.editorOpen)
        click(Strings.editorDiscard)
        assertTrue(hasNode("kept") && hasNode("gone"))
        clickDescription(Strings.editorDelete)
        assertFalse(hasNode("gone"))
        click("kept")
        assertEquals("open kept.json", state.calls.last())

        click(Strings.editorSave)
        assertTrue(hasNode(Strings.editorProjectName.uppercase()))
        typeLast("Draft", "  ")
        pick(Strings.editorSave)
        assertEquals(listOf("save", "saveAs Untitled"), state.calls.takeLast(2))
        click(Strings.editorSaveAs)
        click(Strings.cancelBtn)
    }

    @Test
    fun `open says when there are no projects, and a saved project needs no name`() = runDesktopComposeUiTest(1400, 1200) {
        val state = FakeEditorState(saveSucceeds = true)
        showDark { ProjectToolbarActions(state) }
        click(Strings.editorOpen)
        assertTrue(hasNode(Strings.editorNoProjects))
        click(Strings.cancelBtn)
        click(Strings.editorSave)
        assertEquals(listOf("save"), state.calls)
        assertFalse(hasNode(Strings.editorProjectName.uppercase()))
    }

    @Test
    fun `the export dialog lists issues, previews the label and registers`() = runDesktopComposeUiTest(1400, 1400) {
        val result = RegisterResult(File(temp, "spec.json"), File(temp, "registry.json"))
        val state = FakeEditorState(
            StyleSpec(id = "1", name = "Bar"),
            issues = ExportIssue.entries.toList(),
            registerResult = result,
        )
        showDark { ProjectToolbarActions(state) }
        click(Strings.editorExport)
        assertTrue(hasNode(Strings.editorExportErrId) && hasNode(Strings.editorExportErrNoElements))
        assertTrue(hasNode(Strings.editorExportErrKeyframes))
        assertTrue(hasNode(EditorStrings.exportLabelPreview(EditorStrings.styleLabelFormat("1", "Bar"))))
        typeLast("Bar", "Renamed")
        typeLast("1", "95")
        assertEquals("95" to "Renamed", state.spec.id to state.spec.name)
        click(Strings.cancelBtn)

        state.issues = emptyList()
        click(Strings.editorExport)
        click(Strings.editorRegisterBuild)
        assertEquals("register", state.calls.last())
        assertTrue(hasNode(Strings.editorRegisterDoneTitle))
        click(Strings.ok)
        assertFalse(hasNode(Strings.editorRegisterDoneTitle))
    }

    private companion object {
        const val EditorViewModel_VINE = org.churchpresenter.lottiegen.editor.EditorViewModel.VINE_TEMPLATE_RESOURCE
    }
}
