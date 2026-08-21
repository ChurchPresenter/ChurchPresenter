package org.churchpresenter.lottiegen.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.persistence.StyleSpecStorage
import org.churchpresenter.lottiegen.spec.SpecJson
import org.churchpresenter.lottiegen.spec.SpecStyleGenerator
import org.churchpresenter.lottiegen.spec.StyleSpec
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * One bundled template spec offered by the New dialog: [styleId] links a port to its
 * style's (localized) label; [labelKey] names a bundle string for standalone demos.
 */
data class BundledTemplate(val resource: String, val styleId: String? = null, val labelKey: String? = null)

/**
 * ViewModel for the developer Style Editor. Owns the draft [StyleSpec] and the sample
 * [testConfig], and regenerates the preview JSON (debounced) through the same
 * interpreter shipped spec styles use. A half-edited spec must degrade to a status
 * message — generation errors never crash the editor.
 */
class EditorViewModel(
    private val scope: CoroutineScope,
    initialSpec: StyleSpec
) : EditorState {

    override var spec by mutableStateOf(initialSpec)
        private set

    override var testConfig by mutableStateOf(
        LottieGenConfig(nameText = "John Smith", infoText = "Worship Leader")
    )
        private set

    override var generatedJson by mutableStateOf<String?>(null)
        private set

    override var statusText by mutableStateOf("")
        private set

    override var selectedElementId by mutableStateOf<String?>(null)
        private set

    override var matrixMode by mutableStateOf(false)
        private set

    override var matrixCells by mutableStateOf<List<MatrixCell>>(emptyList())
        private set

    override var dirty by mutableStateOf(false)
        private set

    override var projectName by mutableStateOf(initialSpec.name)
        private set

    override var currentProjectFile by mutableStateOf<File?>(null)
        private set

    override val inFrames: Int get() = (testConfig.animDuration * FR).roundToInt()

    override val totalFrames: Int
        get() = inFrames * 2 + (testConfig.holdDuration * FR).roundToInt()

    private var generateJob: Job? = null
    private var matrixJob: Job? = null
    private val json = Json { prettyPrint = true }

    init {
        scheduleGenerate(0)
    }

    override fun updateSpec(transform: (StyleSpec) -> StyleSpec) {
        spec = transform(spec)
        dirty = true
        scheduleGenerate()
        scheduleMatrix()
    }

    override fun updateTestConfig(transform: (LottieGenConfig) -> LottieGenConfig) {
        testConfig = transform(testConfig)
        scheduleGenerate()
        scheduleMatrix()
    }

    override fun selectElement(id: String?) {
        selectedElementId = id
    }

    override fun setMatrixModeEnabled(enabled: Boolean) {
        matrixMode = enabled
        if (enabled) scheduleMatrix(0)
    }

    // --- Projects ---

    override fun newProject(templateResource: String?) {
        spec = if (templateResource != null) templateSpec(templateResource) else StyleSpec()
        projectName = spec.name
        currentProjectFile = null
        dirty = false
        selectedElementId = null
        scheduleGenerate(0)
        scheduleMatrix(0)
    }

    override fun openProject(file: File): Boolean {
        val loaded = StyleSpecStorage.load(file) ?: return false
        spec = loaded
        projectName = loaded.name.ifEmpty { file.nameWithoutExtension }
        currentProjectFile = file
        dirty = false
        selectedElementId = null
        scheduleGenerate(0)
        scheduleMatrix(0)
        return true
    }

    override fun saveProject(): Boolean {
        val file = currentProjectFile ?: return false
        val toSave = spec.copy(name = projectName)
        spec = toSave
        val ok = StyleSpecStorage.save(toSave, file)
        if (ok) dirty = false
        return ok
    }

    override fun saveProjectAs(name: String) {
        projectName = name
        val toSave = spec.copy(name = name)
        spec = toSave
        val file = StyleSpecStorage.fileForName(name)
        if (StyleSpecStorage.save(toSave, file)) {
            currentProjectFile = file
            dirty = false
        }
    }

    override fun validateForExport(): List<ExportIssue> {
        val issues = mutableListOf<ExportIssue>()
        val id = spec.id.trim()
        // A registered id is allowed — that is the replace-an-existing-style flow; the
        // dialog surfaces it as an informational notice, not an error.
        if (id.toIntOrNull() == null) {
            issues.add(ExportIssue.BAD_ID)
        }
        if (spec.elements.isEmpty()) issues.add(ExportIssue.NO_ELEMENTS)
        val badKeyframes = spec.elements.flatMap { it.tracks }.any { track ->
            (listOf(track.keyframes) + track.alignOverrides.values).any { keyframes ->
                keyframes.isEmpty() ||
                    keyframes.any { it.pct < 0.0 || it.pct > 100.0 } ||
                    keyframes.zipWithNext().any { (a, b) -> a.pct > b.pct }
            }
        }
        if (badKeyframes) issues.add(ExportIssue.BAD_KEYFRAMES)
        return issues
    }

    // Exports the spec as-is: its name field is the style's picker label, edited in the
    // Export dialog (distinct from the editor project name).
    override fun exportTo(file: File): Boolean = StyleSpecStorage.save(spec, file)

    override fun registerIntoBuild(): RegisterResult? {
        // Defense in depth: never trust UI gating alone — an invalid id must not reach
        // the registry (a blank id once slipped through and wrote a broken entry).
        if (validateForExport().isNotEmpty()) return null
        val stylesDir = BuildRegistrar.locateStylesDir() ?: return null
        return try {
            BuildRegistrar.register(spec.copy(id = spec.id.trim()), stylesDir)
        } catch (e: Exception) {
            statusText = "Error: ${e.message}"
            null
        }
    }

    /**
     * Regenerates the 12 matrix cells (3 alignments × logo × bg), sequentially off the
     * UI thread, only while the matrix view is actually showing — a full regenerate is
     * 12 interpreter runs, too expensive to keep warm in the background.
     */
    private fun scheduleMatrix(delayMs: Long = 500) {
        if (!matrixMode) return
        matrixJob?.cancel()
        matrixJob = scope.launch {
            delay(delayMs)
            try {
                val cells = withContext(Dispatchers.Default) {
                    buildList {
                        for (align in listOf("left", "center", "right")) {
                            for (logo in listOf(false, true)) {
                                for (bg in listOf(false, true)) {
                                    val cfg = testConfig.copy(
                                        align = align,
                                        logoEnabled = logo && testConfig.logoData != null,
                                        bgEnabled = bg
                                    )
                                    val lottieJson = LottieGenerator.generate(cfg, SpecStyleGenerator(spec))
                                    add(MatrixCell(align, logo, bg, lottieJson.toString()))
                                }
                            }
                        }
                    }
                }
                matrixCells = cells
            } catch (e: Exception) {
                statusText = "Error: ${e.message}"
            }
        }
    }

    private fun scheduleGenerate(delayMs: Long = 300) {
        generateJob?.cancel()
        generateJob = scope.launch {
            delay(delayMs)
            try {
                val generator = SpecStyleGenerator(spec)
                val lottieJson = withContext(Dispatchers.Default) {
                    LottieGenerator.generate(testConfig, generator)
                }
                val jsonString = withContext(Dispatchers.Default) {
                    json.encodeToString(JsonObject.serializer(), lottieJson)
                }
                generatedJson = jsonString
                statusText = generator.lastWarnings.joinToString("; ")
            } catch (e: Exception) {
                statusText = "Error: ${e.message}"
            }
        }
    }

    companion object {
        private const val FR = 60

        /**
         * Loads a bundled template spec; falls back to a blank spec if unreadable.
         * IOException covers a stale classpath jar (rebuilt while the editor is running) —
         * the read explodes with a ZipException mid-stream instead of returning null above.
         */
        fun templateSpec(resource: String = TEMPLATE_RESOURCE): StyleSpec {
            val stream = EditorViewModel::class.java.getResourceAsStream(resource)
                ?: return StyleSpec()
            return try {
                SpecJson.decode(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } catch (_: IllegalArgumentException) {
                StyleSpec()
            } catch (_: IOException) {
                StyleSpec()
            }
        }

        /** The bundled Style 1 port — the editor's default/example template. */
        const val TEMPLATE_RESOURCE = "/styles/style1_bar_port.json"

        /** Vine demo: curved path + trim draw-on + staggered pivoted leaves. */
        const val VINE_TEMPLATE_RESOURCE = "/styles/demo_vine.json"

        /**
         * All bundled templates offered by the New dialog. [BundledTemplate.styleId] links a
         * port to its compiled style (for the localized label); null = standalone demo.
         */
        val BUNDLED_TEMPLATES: List<BundledTemplate> = listOf(
            BundledTemplate(TEMPLATE_RESOURCE, styleId = "1"),
            BundledTemplate("/styles/style2_boxed_port.json", styleId = "2"),
            BundledTemplate("/styles/style3_circular_port.json", styleId = "3"),
            BundledTemplate("/styles/style4_banner_port.json", styleId = "4"),
            BundledTemplate("/styles/style5_gradient_bar_port.json", styleId = "5"),
            BundledTemplate("/styles/style6_line_split_port.json", styleId = "6"),
            BundledTemplate("/styles/style7_random_fade_port.json", styleId = "7"),
            BundledTemplate("/styles/style8_diagonal_port.json", styleId = "8"),
            BundledTemplate("/styles/style9_diagonal_wipe_port.json", styleId = "9"),
            BundledTemplate("/styles/style10_double_line_port.json", styleId = "10"),
            BundledTemplate("/styles/style11_news_ticker_port.json", styleId = "11"),
            BundledTemplate("/styles/style12_news_badge_port.json", styleId = "12"),
            BundledTemplate(VINE_TEMPLATE_RESOURCE, labelKey = "editor_new_from_vine"),
            BundledTemplate("/styles/demo_frame_draw.json", labelKey = "editor_new_from_frame_draw"),
            BundledTemplate("/styles/demo_flourish.json", labelKey = "editor_new_from_flourish"),
            BundledTemplate("/styles/demo_swing_sign.json", labelKey = "editor_new_from_swing_sign"),
            BundledTemplate("/styles/demo_marquee.json", labelKey = "editor_new_from_marquee"),
            BundledTemplate("/styles/demo_heartbeat.json", labelKey = "editor_new_from_heartbeat"),
            BundledTemplate("/styles/demo_sparkle.json", labelKey = "editor_new_from_sparkle"),
            BundledTemplate("/styles/demo_cross.json", labelKey = "editor_new_from_cross"),
            BundledTemplate("/styles/demo_rays.json", labelKey = "editor_new_from_rays"),
            BundledTemplate("/styles/demo_stained_glass.json", labelKey = "editor_new_from_stained_glass"),
            BundledTemplate("/styles/demo_ichthys.json", labelKey = "editor_new_from_ichthys"),
            BundledTemplate("/styles/demo_christmas_star.json", labelKey = "editor_new_from_christmas_star"),
            BundledTemplate("/styles/demo_easter_dawn.json", labelKey = "editor_new_from_easter_dawn"),
            BundledTemplate("/styles/demo_open_bible.json", labelKey = "editor_new_from_open_bible"),
            BundledTemplate("/styles/demo_dove.json", labelKey = "editor_new_from_dove"),
            BundledTemplate("/styles/demo_candle.json", labelKey = "editor_new_from_candle"),
            BundledTemplate("/styles/demo_steeple.json", labelKey = "editor_new_from_steeple"),
            BundledTemplate("/styles/demo_worship_notes.json", labelKey = "editor_new_from_worship_notes"),
            BundledTemplate("/styles/demo_flame.json", labelKey = "editor_new_from_flame"),
            BundledTemplate("/styles/demo_crown.json", labelKey = "editor_new_from_crown"),
            BundledTemplate("/styles/demo_staff.json", labelKey = "editor_new_from_staff"),
            BundledTemplate("/styles/demo_chalice.json", labelKey = "editor_new_from_chalice")
        )
    }
}
