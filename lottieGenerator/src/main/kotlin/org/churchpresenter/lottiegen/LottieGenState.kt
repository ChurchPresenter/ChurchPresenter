package org.churchpresenter.lottiegen

import org.churchpresenter.lottiegen.model.ColorTheme
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.Preset
import java.io.File

/**
 * State interface for ControlPanel — separates the composable from the ViewModel type
 * so ViewModels are not passed as Composable parameters.
 */
interface LottieGenState {
    val config: LottieGenConfig
    val presets: List<Preset>
    val colorThemes: List<ColorTheme>
    val availableLogos: List<String>
    val hasOutputDir: Boolean

    fun updateConfig(transform: (LottieGenConfig) -> LottieGenConfig)

    fun saveColorTheme()
    fun loadColorTheme(index: Int)
    fun deleteColorTheme(index: Int)

    fun clearLogo()
    fun selectLogo(name: String)
    fun importAndLoadLogo(sourceFile: File)

    fun saveLowerThird(): File?
    fun downloadJson(dir: File?): File?
    fun savePreset()
    fun applyStyleToAll()
    fun batchDownloadAll(dir: File? = null)
    fun loadPreset(index: Int)
    fun deletePreset(index: Int)
    fun batchImportPresets(input: String): Pair<Int, Int>
    fun updateStatusText(text: String)
}
