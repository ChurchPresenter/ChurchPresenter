package org.churchpresenter.lottiegen

import org.churchpresenter.lottiegen.model.ColorTheme
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.Preset
import java.io.File

/** The lower third being edited, and the colour themes it can be dressed in. */
interface ConfigState {
    val config: LottieGenConfig
    val colorThemes: List<ColorTheme>

    fun updateConfig(transform: (LottieGenConfig) -> LottieGenConfig)
    fun saveColorTheme()
    fun loadColorTheme(index: Int)
    fun deleteColorTheme(index: Int)
}

/** The logo the lower third carries, and the library it is chosen from. */
interface LogoState {
    val availableLogos: List<String>

    fun clearLogo()
    fun selectLogo(name: String)
    fun importAndLoadLogo(sourceFile: File)
}

/** Saved presets, and the bulk operations over them. */
interface PresetState {
    val presets: List<Preset>

    fun savePreset()
    fun loadPreset(index: Int)
    fun deletePreset(index: Int)
    fun applyStyleToAll()
    fun batchImportPresets(input: String): Pair<Int, Int>
}

/** Writing the generated JSON out, and saying what happened. */
interface ExportState {
    val hasOutputDir: Boolean

    fun saveLowerThird(): File?
    fun downloadJson(dir: File?): File?
    fun batchDownloadAll(dir: File? = null)
    fun updateStatusText(text: String)
}

/**
 * State interface for ControlPanel — separates the composable from the ViewModel type
 * so ViewModels are not passed as Composable parameters.
 *
 * Composed from four smaller interfaces rather than listing sixteen members: the panel takes the
 * whole thing, but each section only needs one of them, and the split says which.
 */
interface LottieGenState : ConfigState, LogoState, PresetState, ExportState
