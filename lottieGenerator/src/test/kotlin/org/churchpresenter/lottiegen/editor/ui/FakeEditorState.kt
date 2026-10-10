package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.lottiegen.editor.EditorState
import org.churchpresenter.lottiegen.editor.ExportIssue
import org.churchpresenter.lottiegen.editor.MatrixCell
import org.churchpresenter.lottiegen.editor.RegisterResult
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.spec.StyleSpec
import java.io.File

internal class FakeEditorState(
    initial: StyleSpec = StyleSpec(id = "90", name = "Draft"),
    var issues: List<ExportIssue> = emptyList(),
    var saveSucceeds: Boolean = false,
    var registerResult: RegisterResult? = null,
) : EditorState {
    override var spec by mutableStateOf(initial)
    override var testConfig by mutableStateOf(LottieGenConfig())
    override var selectedElementId by mutableStateOf<String?>(null)
    override var generatedJson by mutableStateOf<String?>(null)
    override var statusText by mutableStateOf("")
    override val inFrames: Int get() = 60
    override val totalFrames: Int get() = 240
    override var matrixMode by mutableStateOf(false)
    override var matrixCells by mutableStateOf<List<MatrixCell>>(emptyList())
    override var dirty by mutableStateOf(false)
    override var projectName by mutableStateOf("Draft")
    override val currentProjectFile: File? = null
    val calls = mutableListOf<String>()

    override fun updateSpec(transform: (StyleSpec) -> StyleSpec) { spec = transform(spec) }
    override fun updateTestConfig(transform: (LottieGenConfig) -> LottieGenConfig) {
        testConfig = transform(testConfig)
    }
    override fun selectElement(id: String?) { selectedElementId = id }
    override fun setMatrixModeEnabled(enabled: Boolean) { matrixMode = enabled }
    override fun newProject(templateResource: String?) { calls += "new $templateResource" }
    override fun openProject(file: File): Boolean { calls += "open ${file.name}"; return true }
    override fun saveProject(): Boolean { calls += "save"; return saveSucceeds }
    override fun saveProjectAs(name: String) { calls += "saveAs $name" }
    override fun validateForExport(): List<ExportIssue> = issues
    override fun exportTo(file: File): Boolean { calls += "export"; return true }
    override fun registerIntoBuild(): RegisterResult? { calls += "register"; return registerResult }
}
