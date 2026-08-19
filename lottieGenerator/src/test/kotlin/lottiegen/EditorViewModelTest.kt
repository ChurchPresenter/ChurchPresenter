package lottiegen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import lottiegen.editor.EditorViewModel
import lottiegen.editor.ExportIssue
import lottiegen.editor.NewElementKind
import lottiegen.editor.addElement
import lottiegen.spec.AnimProperty
import lottiegen.spec.AnimTrack
import lottiegen.spec.RectElement
import lottiegen.spec.SpecKeyframe
import lottiegen.spec.StyleSpec
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
 * The Style Editor's view model — the draft spec, project open/save, and export validation.
 *
 * Like [LottieGenViewModelTest] this is a plain class holding Compose snapshot state, so it drives
 * headlessly. Generation is debounced and runs on a real dispatcher, so tests wait on the output
 * appearing rather than on a duration.
 *
 * The behaviour worth defending most is that **a half-edited spec degrades to a status message**:
 * the editor is used while a spec is deliberately incomplete, and an exception there would take
 * the window down with unsaved work.
 */
class EditorViewModelTest {

    private lateinit var temp: File
    private lateinit var savedHome: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("lottiegen-editor-test").toFile()
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

    private fun spec(name: String = "Draft", id: String = "42"): StyleSpec =
        StyleSpec(id = id, name = name).addElement(NewElementKind.RECT)

    /** An editor whose initial render has completed (construction schedules it with no delay). */
    private fun editor(initial: StyleSpec = spec()): EditorViewModel {
        val vm = EditorViewModel(scope, initial)
        waitFor("the initial render") { vm.generatedJson != null }
        return vm
    }

    /**
     * An editor over a spec that may not render at all — validation does not need a preview, and
     * a spec with an empty keyframe list is exactly the half-edited state the editor tolerates by
     * reporting a status instead of producing output.
     */
    private fun validating(initial: StyleSpec) = EditorViewModel(scope, initial)

    // ── Initial state and generation ──────────────────────────────────────────

    @Test
    fun `construction renders the spec straight away`() {
        val vm = editor()
        val json = assertNotNull(vm.generatedJson)
        assertTrue(json.contains("\"layers\""), "a Lottie document came out")
        assertEquals("", vm.statusText, "no error")
        assertTrue(!vm.dirty, "a freshly opened project is not dirty")
    }

    @Test
    fun `the project name starts as the spec's name`() {
        assertEquals("My Style", editor(spec(name = "My Style")).projectName)
    }

    @Test
    fun `editing the spec marks the project dirty and regenerates`() {
        val vm = editor()
        val before = assertNotNull(vm.generatedJson)

        vm.updateSpec { it.addElement(NewElementKind.ELLIPSE) }
        waitFor("the regenerated preview") { vm.generatedJson != before }

        assertTrue(vm.dirty, "there are unsaved changes")
        assertEquals(2, vm.spec.elements.size)
    }

    @Test
    fun `editing the sample text regenerates without marking the spec dirty`() {
        // The test config is a preview aid, not part of the document.
        val vm = editor()
        val before = assertNotNull(vm.generatedJson)

        vm.updateTestConfig { it.copy(nameText = "Someone Else") }
        waitFor("the regenerated preview") { vm.generatedJson != before }

        assertEquals("Someone Else", vm.testConfig.nameText)
        assertTrue(!vm.dirty, "the document itself did not change")
    }

    @Test
    fun `frame counts follow the sample timing`() {
        val vm = editor()
        vm.updateTestConfig { it.copy(animDuration = 2f, holdDuration = 3f) }

        assertTrue(vm.inFrames > 0, "the transition has a length")
        // The timeline is in + hold + out, so the total exceeds the two transitions by the hold.
        assertTrue(vm.totalFrames > vm.inFrames * 2, "the hold sits between the two transitions")

        // Doubling the transition doubles its frame count; the relationship is linear.
        val singleIn = vm.inFrames
        vm.updateTestConfig { it.copy(animDuration = 4f) }
        assertEquals(singleIn * 2, vm.inFrames)
    }

    @Test
    fun `selecting an element is remembered and can be cleared`() {
        val vm = editor()
        vm.selectElement("rect1")
        assertEquals("rect1", vm.selectedElementId)
        vm.selectElement(null)
        assertNull(vm.selectedElementId)
    }

    // ── Projects ─────────────────────────────────────────────────────────────

    @Test
    fun `a new project resets the document and its dirty state`() {
        val vm = editor()
        vm.updateSpec { it.addElement(NewElementKind.ELLIPSE) }
        vm.selectElement("rect1")
        waitFor("dirty") { vm.dirty }

        vm.newProject(null)
        waitFor("the blank render") { vm.generatedJson != null }

        assertTrue(vm.spec.elements.isEmpty(), "a blank document")
        assertTrue(!vm.dirty)
        assertNull(vm.selectedElementId, "the old selection does not survive")
        assertNull(vm.currentProjectFile, "and it has no file yet")
    }

    @Test
    fun `saving as a name writes a file and clears dirty`() {
        val vm = editor()
        vm.updateSpec { it.addElement(NewElementKind.ELLIPSE) }
        waitFor("dirty") { vm.dirty }

        vm.saveProjectAs("My Lower Third")

        assertEquals("My Lower Third", vm.projectName)
        assertEquals("My Lower Third", vm.spec.name, "the name is written into the document")
        val file = assertNotNull(vm.currentProjectFile)
        assertTrue(file.exists())
        assertTrue(!vm.dirty)
    }

    @Test
    fun `saving without a file first does nothing`() {
        val vm = editor()
        assertTrue(!vm.saveProject(), "there is nowhere to save to yet")
    }

    @Test
    fun `saving again writes to the same file`() {
        val vm = editor()
        vm.saveProjectAs("Reused")
        val file = assertNotNull(vm.currentProjectFile)

        vm.updateSpec { it.addElement(NewElementKind.ELLIPSE) }
        waitFor("dirty") { vm.dirty }
        assertTrue(vm.saveProject())

        assertEquals(file, vm.currentProjectFile, "no second file appeared")
        assertTrue(!vm.dirty)
    }

    @Test
    fun `a saved project reopens with its elements intact`() {
        val vm = editor()
        vm.updateSpec { it.addElement(NewElementKind.ELLIPSE).addElement(NewElementKind.NAME_TEXT) }
        vm.saveProjectAs("Round Trip")
        val file = assertNotNull(vm.currentProjectFile)

        val reopened = editor(StyleSpec())
        assertTrue(reopened.openProject(file))
        waitFor("the reopened render") { reopened.generatedJson != null }

        assertEquals("Round Trip", reopened.projectName)
        assertEquals(3, reopened.spec.elements.size)
        assertEquals(file, reopened.currentProjectFile)
        assertTrue(!reopened.dirty, "just-opened is not dirty")
    }

    @Test
    fun `opening a corrupt file is refused and leaves the document alone`() {
        val vm = editor(spec(name = "Keep Me"))
        val corrupt = File(temp, "broken.json").apply { writeText("{ not json") }

        assertTrue(!vm.openProject(corrupt), "reported as a failure")
        assertEquals("Keep Me", vm.projectName, "the open document survived")
        assertNull(vm.currentProjectFile)
    }

    @Test
    fun `opening a file whose spec has no name falls back to the file name`() {
        val file = File(temp, "Unnamed Project.json")
        StyleSpecStorageWriteHelper.write(StyleSpec(name = ""), file)

        val vm = editor()
        assertTrue(vm.openProject(file))
        assertEquals("Unnamed Project", vm.projectName)
    }

    // ── Export validation ────────────────────────────────────────────────────

    @Test
    fun `a well-formed spec has nothing to report`() {
        assertTrue(editor(spec(id = "42")).validateForExport().isEmpty())
    }

    @Test
    fun `a non-numeric id is reported`() {
        assertTrue(ExportIssue.BAD_ID in validating(spec(id = "")).validateForExport())
        assertTrue(ExportIssue.BAD_ID in validating(spec(id = "not-a-number")).validateForExport())
    }

    @Test
    fun `a spec with no elements is reported`() {
        assertTrue(ExportIssue.NO_ELEMENTS in validating(StyleSpec(id = "42")).validateForExport())
    }

    @Test
    fun `keyframes outside zero to a hundred are reported`() {
        val bad = StyleSpec(
            id = "42",
            elements = listOf(
                RectElement(
                    id = "r1",
                    tracks = listOf(AnimTrack(AnimProperty.OPACITY, listOf(SpecKeyframe(-5.0, listOf(0.0))))),
                )
            ),
        )
        assertTrue(ExportIssue.BAD_KEYFRAMES in validating(bad).validateForExport())
    }

    @Test
    fun `keyframes running backwards are reported`() {
        val bad = StyleSpec(
            id = "42",
            elements = listOf(
                RectElement(
                    id = "r1",
                    tracks = listOf(
                        AnimTrack(
                            AnimProperty.OPACITY,
                            listOf(SpecKeyframe(80.0, listOf(0.0)), SpecKeyframe(20.0, listOf(1.0))),
                        )
                    ),
                )
            ),
        )
        assertTrue(ExportIssue.BAD_KEYFRAMES in validating(bad).validateForExport())
    }

    @Test
    fun `a track with no keyframes at all is reported`() {
        val bad = StyleSpec(
            id = "42",
            elements = listOf(RectElement(id = "r1", tracks = listOf(AnimTrack(AnimProperty.OPACITY, emptyList())))),
        )
        assertTrue(ExportIssue.BAD_KEYFRAMES in validating(bad).validateForExport())
    }

    @Test
    fun `an invalid spec is refused registration even if the UI asks`() {
        // Defense in depth — a blank id once slipped through and wrote a broken registry entry.
        assertNull(validating(spec(id = "")).registerIntoBuild())
    }

    @Test
    fun `exporting writes the spec to the chosen file`() {
        val vm = editor(spec(name = "Exported"))
        val target = File(temp, "exported.json")

        assertTrue(vm.exportTo(target))
        assertTrue(target.exists())
        assertTrue(target.readText().contains("Exported"))
    }

    // ── Matrix preview ───────────────────────────────────────────────────────

    @Test
    fun `the matrix stays cold until it is shown`() {
        // Twelve interpreter runs is too expensive to keep warm in the background.
        val vm = editor()
        vm.updateSpec { it.addElement(NewElementKind.ELLIPSE) }
        assertTrue(vm.matrixCells.isEmpty(), "nothing was rendered for a hidden matrix")
        assertTrue(!vm.matrixMode)
    }

    @Test
    fun `enabling the matrix renders every alignment and toggle combination`() {
        val vm = editor()
        vm.setMatrixModeEnabled(true)
        waitFor("the matrix cells") { vm.matrixCells.size == 12 }

        assertTrue(vm.matrixMode)
        assertEquals(setOf("left", "center", "right"), vm.matrixCells.map { it.align }.toSet())
        assertEquals(12, vm.matrixCells.map { it.align to (it.logo to it.bg) }.distinct().size)
    }

    @Test
    fun `turning the matrix off stops it being refreshed`() {
        val vm = editor()
        vm.setMatrixModeEnabled(true)
        waitFor("the matrix cells") { vm.matrixCells.isNotEmpty() }

        vm.setMatrixModeEnabled(false)
        assertTrue(!vm.matrixMode)
    }

    // ── Degrading rather than crashing ───────────────────────────────────────

    @Test
    fun `a spec the generator cannot render reports a status instead of throwing`() {
        // A half-edited spec is the editor's normal state; it must never take the window down.
        val broken = StyleSpec(
            id = "42",
            elements = listOf(
                RectElement(
                    id = "r1",
                    tracks = listOf(AnimTrack(AnimProperty.TRIM, listOf(SpecKeyframe(0.0, emptyList())))),
                )
            ),
        )
        val vm = EditorViewModel(scope, broken)
        // Either it renders or it reports — what must not happen is an exception escaping.
        waitFor("a render or a status") { vm.generatedJson != null || vm.statusText.isNotEmpty() }
    }
}

/** Writes a spec without going through the view model, for open-path fixtures. */
private object StyleSpecStorageWriteHelper {
    fun write(spec: StyleSpec, file: File) {
        file.writeText(lottiegen.spec.SpecJson.encode(spec), Charsets.UTF_8)
    }
}
