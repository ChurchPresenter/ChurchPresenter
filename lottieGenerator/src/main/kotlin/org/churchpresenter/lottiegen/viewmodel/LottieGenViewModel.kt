package org.churchpresenter.lottiegen.viewmodel

import kotlinx.coroutines.CancellationException
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
import org.churchpresenter.lottiegen.LottieGenState
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.ColorTheme
import org.churchpresenter.lottiegen.model.ColorThemeColors
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.Preset
import org.churchpresenter.lottiegen.persistence.ColorThemeStorage
import org.churchpresenter.lottiegen.persistence.LogoStorage
import org.churchpresenter.lottiegen.persistence.PresetStorage
import java.io.File
import java.time.Instant

/**
 * How long to wait before regenerating. A dragged slider or a held key settles quickly, so those
 * paths pass the shorter window; the default covers a control whose value arrives in one step.
 */
private const val SETTLE_DEBOUNCE_MS = 300L
private const val TYPING_DEBOUNCE_MS = 100L


class LottieGenViewModel(
    private val scope: CoroutineScope,
    private val outputDir: File? = null,
    private val onFileSaved: (() -> Unit)? = null,
    canvasWidth: Int? = null,
    canvasHeight: Int? = null
) : LottieGenState {
    /** True when launched inside ChurchPresenter with a configured output folder */
    override val hasOutputDir: Boolean get() = outputDir != null
    override var config by mutableStateOf(
        LottieGenConfig(canvasW = canvasWidth ?: 1920, canvasH = canvasHeight ?: 1080),
    )
        private set

    var generatedJson by mutableStateOf<String?>(null)
        private set

    override var presets by mutableStateOf<List<Preset>>(emptyList())
        private set

    override var colorThemes by mutableStateOf<List<ColorTheme>>(emptyList())
        private set

    override var availableLogos by mutableStateOf<List<String>>(emptyList())
        private set

    var statusText by mutableStateOf("")
        private set

    private var generateJob: Job? = null
    private val json = Json { prettyPrint = true }

    init {
        presets = PresetStorage.load()
        colorThemes = ColorThemeStorage.load()
        availableLogos = LogoStorage.listLogos()
        scheduleGenerate()
    }

    override fun updateConfig(transform: (LottieGenConfig) -> LottieGenConfig) {
        config = transform(config)
        scheduleGenerate()
    }

    private fun scheduleGenerate(delayMs: Long = SETTLE_DEBOUNCE_MS) {
        generateJob?.cancel()
        generateJob = scope.launch {
            delay(delayMs)
            try {
                val lottieJson = withContext(Dispatchers.Default) {
                    LottieGenerator.generate(config)
                }
                val jsonString = withContext(Dispatchers.Default) {
                    json.encodeToString(JsonObject.serializer(), lottieJson)
                }
                generatedJson = jsonString
                statusText = ""
            } catch (e: CancellationException) {
                // A generic `catch (e: Exception)` here also swallowed cancellation, which is how a
                // superseded regeneration went on to overwrite the status of the one that replaced it.
                throw e
            } catch (e: IllegalStateException) {
                statusText = "Error: ${e.message}"
            } catch (e: IllegalArgumentException) {
                statusText = "Error: ${e.message}"
            }
        }
    }

    fun generateNow() {
        generateJob?.cancel()
        generateJob = scope.launch {
            try {
                val lottieJson = withContext(Dispatchers.Default) {
                    LottieGenerator.generate(config)
                }
                val jsonString = withContext(Dispatchers.Default) {
                    json.encodeToString(JsonObject.serializer(), lottieJson)
                }
                generatedJson = jsonString
                statusText = ""
            } catch (e: CancellationException) {
                // A generic `catch (e: Exception)` here also swallowed cancellation, which is how a
                // superseded regeneration went on to overwrite the status of the one that replaced it.
                throw e
            } catch (e: IllegalStateException) {
                statusText = "Error: ${e.message}"
            } catch (e: IllegalArgumentException) {
                statusText = "Error: ${e.message}"
            }
        }
    }

    // --- Presets ---

    override fun savePreset() {
        val nameText = config.nameText.trim().ifEmpty { "Untitled" }
        val infoText = config.infoText.trim()
        val presetConfig = config.copy(logoData = null, logoW = 0, logoH = 0)
        val existingIndex = presets.indexOfFirst {
            it.config.nameText.trim() == nameText && it.config.infoText.trim() == infoText
        }
        if (existingIndex >= 0) {
            val updated = presets[existingIndex].copy(savedAt = Instant.now().toString(), config = presetConfig)
            presets = presets.toMutableList().also { it[existingIndex] = updated }
        } else {
            val name = uniqueName(nameText, presets.map { it.name }.toSet())
            presets = presets + Preset(name, Instant.now().toString(), presetConfig)
        }
        PresetStorage.save(presets)
    }

    override fun batchImportPresets(input: String): Pair<Int, Int> {
        var added = 0
        var updated = 0
        val now = Instant.now().toString()
        val baseConfig = config.copy(logoData = null, logoW = 0, logoH = 0)
        val list = presets.toMutableList()

        input.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("|") }
            .forEach { line ->
                val parts = line.split("|", limit = 2)
                val nameText = parts[0].trim().ifEmpty { return@forEach }
                val infoText = parts.getOrNull(1)?.trim() ?: ""
                val presetConfig = baseConfig.copy(nameText = nameText, infoText = infoText)
                val idx = list.indexOfFirst {
                    it.config.nameText.trim() == nameText && it.config.infoText.trim() == infoText
                }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(savedAt = now, config = presetConfig)
                    updated++
                } else {
                    val name = uniqueName(nameText, list.map { it.name }.toSet())
                    list.add(Preset(name, now, presetConfig))
                    added++
                }
            }

        presets = list
        PresetStorage.save(presets)
        return Pair(added, updated)
    }

    override fun updateStatusText(text: String) {
        statusText = text
    }

    override fun loadPreset(index: Int) {
        val preset = presets.getOrNull(index) ?: return
        config = preset.config.copy(
            logoData = config.logoData,
            logoW = config.logoW,
            logoH = config.logoH
        )
        scheduleGenerate(TYPING_DEBOUNCE_MS)
    }

    override fun deletePreset(index: Int) {
        presets = presets.toMutableList().apply { removeAt(index) }
        PresetStorage.save(presets)
    }

    override fun applyStyleToAll() {
        val now = Instant.now().toString()
        val styleConfig = config.copy(logoData = null, logoW = 0, logoH = 0)
        presets = presets.map { preset ->
            preset.copy(
                savedAt = now,
                config = styleConfig.copy(nameText = preset.config.nameText, infoText = preset.config.infoText)
            )
        }
        PresetStorage.save(presets)
        statusText = "Style applied to ${presets.size} items"
    }

    override fun batchDownloadAll(dir: File?) {
        @Suppress("NAME_SHADOWING") val dir = dir ?: outputDir
        val targetDir = dir ?: return
        if (!targetDir.isDirectory) return
        scope.launch {
            var count = 0
            presets.forEach { preset ->
                try {
                    val lottieJson = withContext(Dispatchers.Default) { LottieGenerator.generate(preset.config) }
                    val jsonString = withContext(Dispatchers.Default) {
                        json.encodeToString(JsonObject.serializer(), lottieJson)
                    }
                    val safeName = preset.config.nameText.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val safeInfo = preset.config.infoText.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val baseName = buildString {
                        if (safeName.isNotEmpty()) append(safeName)
                        if (safeInfo.isNotEmpty()) { if (isNotEmpty()) append(" - "); append(safeInfo) }
                    }.ifEmpty { "lower-third" }
                    var num = 1
                    var file: File
                    do { file = File(targetDir, "$baseName - %02d.json".format(num)); num++ } while (file.exists())
                    file.writeText(jsonString)
                    count++
                } catch (_: Exception) {}
            }
            statusText = "Saved $count files"
            onFileSaved?.invoke()
        }
    }

    // --- Color Themes ---

    override fun saveColorTheme() {
        val baseName = "Custom"
        val name = uniqueName(baseName, colorThemes.map { it.name }.toSet())
        val theme = ColorTheme(
            name, ColorThemeColors(
                nameColor = config.nameColor, infoColor = config.infoColor,
                accentColor = config.accentColor, bgColor = config.bgColor,
                borderColor = config.borderColor,
                nameColorAlpha = config.nameColorAlpha, infoColorAlpha = config.infoColorAlpha,
                accentColorAlpha = config.accentColorAlpha, bgColorAlpha = config.bgColorAlpha,
                borderColorAlpha = config.borderColorAlpha
            )
        )
        colorThemes = colorThemes + theme
        ColorThemeStorage.save(colorThemes)
    }

    override fun loadColorTheme(index: Int) {
        val theme = colorThemes.getOrNull(index) ?: return
        config = config.copy(
            nameColor = theme.colors.nameColor, infoColor = theme.colors.infoColor,
            accentColor = theme.colors.accentColor, bgColor = theme.colors.bgColor,
            borderColor = theme.colors.borderColor,
            nameColorAlpha = theme.colors.nameColorAlpha, infoColorAlpha = theme.colors.infoColorAlpha,
            accentColorAlpha = theme.colors.accentColorAlpha, bgColorAlpha = theme.colors.bgColorAlpha,
            borderColorAlpha = theme.colors.borderColorAlpha
        )
        scheduleGenerate(TYPING_DEBOUNCE_MS)
    }

    override fun deleteColorTheme(index: Int) {
        colorThemes = colorThemes.toMutableList().apply { removeAt(index) }
        ColorThemeStorage.save(colorThemes)
    }

    // --- Logo ---

    fun loadLogo(file: File) {
        val data = LogoStorage.loadLogoData(file) ?: return
        config = config.copy(
            logoEnabled = true,
            logoData = data.dataUrl,
            logoW = data.width,
            logoH = data.height,
            logoSelect = file.name
        )
        scheduleGenerate(TYPING_DEBOUNCE_MS)
    }

    override fun importAndLoadLogo(sourceFile: File) {
        val imported = LogoStorage.importLogo(sourceFile) ?: return
        availableLogos = LogoStorage.listLogos()
        loadLogo(imported)
    }

    override fun clearLogo() {
        config = config.copy(logoEnabled = false, logoData = null, logoW = 0, logoH = 0, logoSelect = "")
        scheduleGenerate(TYPING_DEBOUNCE_MS)
    }

    override fun selectLogo(name: String) {
        if (name.isEmpty()) {
            clearLogo()
            return
        }
        val file = LogoStorage.getLogoFile(name)
        loadLogo(file)
    }

    // --- Export ---

    /**
     * Save JSON to a directory. When called without arguments, uses the configured outputDir.
     * Called from the "Save Lower Third" button (embedded mode) or "Download JSON" (standalone).
     */
    override fun downloadJson(dir: File?): File? {
        val jsonStr = generatedJson ?: return null
        val targetDir = dir ?: outputDir ?: return null
        if (!targetDir.isDirectory) return null

        val nameText = config.nameText.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val infoText = config.infoText.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val baseName = buildString {
            if (nameText.isNotEmpty()) append(nameText)
            if (infoText.isNotEmpty()) {
                if (isNotEmpty()) append(" - ")
                append(infoText)
            }
        }.ifEmpty { "lower-third" }

        var num = 1
        var file: File
        do {
            file = File(targetDir, "$baseName - %02d.json".format(num))
            num++
        } while (file.exists())

        file.writeText(jsonStr)
        statusText = "Saved: ${file.name}"
        onFileSaved?.invoke()
        return file
    }

    /**
     * Save to outputDir + auto-save preset (matches old Lottie-Gen "Save Lower Third" behavior).
     * Used when running inside ChurchPresenter.
     */
    override fun saveLowerThird(): File? {
        val file = downloadJson(null) ?: return null
        savePreset()
        return file
    }

    private fun uniqueName(base: String, existing: Set<String>): String {
        if (base !in existing) return base
        var i = 1
        while ("$base - $i" in existing) i++
        return "$base - $i"
    }
}
