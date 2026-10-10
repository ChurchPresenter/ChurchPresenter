package org.churchpresenter.lottiegen.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.lottiegen.LottieGenState
import org.churchpresenter.lottiegen.model.ColorTheme
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.Preset
import java.io.File

internal class FakeLottieGenState(
    initial: LottieGenConfig = LottieGenConfig(),
    override val hasOutputDir: Boolean = false,
    override val colorThemes: List<ColorTheme> = emptyList(),
    override val presets: List<Preset> = emptyList(),
    override val availableLogos: List<String> = emptyList(),
    override val styleThumbnails: Map<String, ImageBitmap> = emptyMap(),
) : LottieGenState {
    override var config by mutableStateOf(initial)
    val calls = mutableListOf<String>()
    var status = ""

    override fun ensureStyleThumbnails() { calls += "thumbnails" }
    override fun updateConfig(transform: (LottieGenConfig) -> LottieGenConfig) { config = transform(config) }
    override fun saveColorTheme() { calls += "saveTheme" }
    override fun loadColorTheme(index: Int) { calls += "loadTheme $index" }
    override fun deleteColorTheme(index: Int) { calls += "deleteTheme $index" }
    override fun clearLogo() { calls += "clearLogo" }
    override fun selectLogo(name: String) { calls += "selectLogo $name" }
    override fun importAndLoadLogo(sourceFile: File) { calls += "importLogo" }
    override fun savePreset() { calls += "savePreset" }
    override fun loadPreset(index: Int) { calls += "loadPreset $index" }
    override fun deletePreset(index: Int) { calls += "deletePreset $index" }
    override fun applyStyleToAll() { calls += "applyAll" }
    override fun batchImportPresets(input: String): Pair<Int, Int> { calls += "batch $input"; return 2 to 1 }
    override fun saveLowerThird(): File? { calls += "saveLowerThird"; return null }
    override fun downloadJson(dir: File?): File? { calls += "download"; return null }
    override fun batchDownloadAll(dir: File?) { calls += "batchDownload $dir" }
    override fun updateStatusText(text: String) { status = text }
}
