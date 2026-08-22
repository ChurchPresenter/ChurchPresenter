package org.churchpresenter.lottiegen.editor

import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.spec.StyleSpec
import java.io.File

/** One rendered cell of the test-matrix view. */
data class MatrixCell(val align: String, val logo: Boolean, val bg: Boolean, val json: String)

/** Problems that block exporting a spec for shipping. */
enum class ExportIssue { BAD_ID, NO_ELEMENTS, BAD_KEYFRAMES }

/**
 * State seam between the Style Editor's UI and its ViewModel — editor composables
 * take this interface, never the ViewModel class (matches the LottieGenState pattern).
 */
/** The document being edited and the sample config it is previewed against. */
interface SpecEditingState {
    /** The spec document being edited. */
    val spec: StyleSpec

    /** The sample operator config the draft is previewed against. */
    val testConfig: LottieGenConfig

    /** Currently selected element id, or null when the layout section is shown. */
    val selectedElementId: String?

    fun updateSpec(transform: (StyleSpec) -> StyleSpec)
    fun updateTestConfig(transform: (LottieGenConfig) -> LottieGenConfig)
    fun selectElement(id: String?)
}

/** What the right-hand pane is showing, and what it has rendered. */
interface EditorPreviewState {
    /** Latest interpreted Lottie JSON for the preview, or null while none generated. */
    val generatedJson: String?

    /** Errors/warnings from the last generation (empty = clean). */
    val statusText: String

    /** Frames in the animate-in phase at the current test config (60 fps). */
    val inFrames: Int

    /** Total composition frames (in + hold + out) at the current test config. */
    val totalFrames: Int

    /** True when the right pane shows the test matrix instead of the live preview. */
    val matrixMode: Boolean

    /** Rendered matrix cells (empty until matrix mode has generated them). */
    val matrixCells: List<MatrixCell>

    fun setMatrixModeEnabled(enabled: Boolean)
}

/** Opening, saving and shipping the project. */
interface EditorProjectState {
    /** Unsaved-changes flag. */
    val dirty: Boolean

    /** Current project name (file-backed projects) or the spec name for drafts. */
    val projectName: String

    /** The file the current project was loaded from / saved to, or null for drafts. */
    val currentProjectFile: File?

    /** Starts a new project from a bundled template resource path, or blank when null. */
    fun newProject(templateResource: String?)
    fun openProject(file: File): Boolean

    /** Saves to [currentProjectFile]; false when there is none yet (needs Save As). */
    fun saveProject(): Boolean
    fun saveProjectAs(name: String)
    fun validateForExport(): List<ExportIssue>

    /** Writes the spec to [file] for committing into the submodule's resources. */
    fun exportTo(file: File): Boolean

    /**
     * Writes the spec + registry entry into the source checkout (code-free shipping).
     * Null when it failed; only callable when [BuildRegistrar.locateStylesDir] found one.
     */
    fun registerIntoBuild(): RegisterResult?
}

/**
 * State seam between the Style Editor's UI and its ViewModel — editor composables
 * take this interface, never the ViewModel class (matches the LottieGenState pattern).
 *
 * Composed from three smaller interfaces: the document being edited, what the preview pane is
 * showing, and the project on disk.
 */
interface EditorState : SpecEditingState, EditorPreviewState, EditorProjectState
