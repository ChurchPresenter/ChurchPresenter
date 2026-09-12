package org.churchpresenter.lottiegen.band

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.ui.Strings
import java.io.File
import java.io.IOException

private const val SETTLE_DEBOUNCE_MS = 300L
private const val PICTURE_TINT_ALPHA = 40

/**
 * The Bible band generator's state: the config being edited, the JSON it currently produces, and
 * the save that hands the file back to the host. Regeneration is debounced the way the main
 * generator's is, so a slider drag costs one render rather than one per pixel.
 */
class BibleLottieGenViewModel(
    private val scope: CoroutineScope,
    private val outputDir: File?,
    private val onFileSaved: ((File) -> Unit)?,
    initial: BibleLottieGenConfig = BibleLottieGenConfig(),
) {
    var config by mutableStateOf(BandConfigStorage.restore(initial))
        private set
    var generatedJson by mutableStateOf<String?>(null)
        private set
    var statusText by mutableStateOf("")
        private set
    var fileName by mutableStateOf(suggestedFileName(config))
        private set

    val timeline: BandTimeline get() = BandTimeline.from(config)

    private var generateJob: Job? = null
    private val json = Json { prettyPrint = true }

    init {
        scheduleGenerate()
    }

    fun updateConfig(transform: (BibleLottieGenConfig) -> BibleLottieGenConfig) {
        val before = config
        config = transform(config)
        if (fileName == suggestedFileName(before)) fileName = suggestedFileName(config)
        scheduleGenerate()
    }

    fun updateFileName(name: String) {
        fileName = name
    }

    /** Reads [file] as the picture for [role]; a file that is not an image is reported, not ignored. */
    fun loadBandImage(role: BandColorRole, file: File) {
        val data = BandImageLoader.load(file)
        if (data == null) {
            statusText = Strings.bandStatusPictureUnreadable(file.name)
            return
        }
        updateConfig {
            val image = BandImage(data.dataUrl, data.width, data.height, file.name)
            it.copy(
                images = it.images + (role to image),
                // A background picture wants to be seen: the first pick drops the fill to a tint
                // unless the alpha was already lowered.
                bgAlpha = if (role == BandColorRole.BACKGROUND && !it.hasBackgroundImage &&
                    it.bgAlpha >= BibleLottieGenConfig.DEFAULT_BG_ALPHA
                ) PICTURE_TINT_ALPHA else it.bgAlpha,
            )
        }
    }

    fun clearBandImage(role: BandColorRole) {
        updateConfig { it.copy(images = it.images - role) }
    }

    /** Writes the current JSON as `<fileName>.json` into [outputDir]; null when there is nothing to save. */
    fun save(): File? {
        val dir = outputDir ?: return null
        val body = generatedJson ?: return null
        val safe = fileName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { DEFAULT_FILE_STEM }
        val file = File(dir, "$safe.json")
        return try {
            dir.mkdirs()
            file.writeText(body)
            BandConfigStorage.save(config)
            statusText = Strings.bandStatusSaved(file.name)
            onFileSaved?.invoke(file)
            file
        } catch (e: IOException) {
            statusText = Strings.bandStatusError(e.message.orEmpty())
            null
        }
    }

    private fun scheduleGenerate() {
        generateJob?.cancel()
        generateJob = scope.launch {
            delay(SETTLE_DEBOUNCE_MS)
            try {
                val lottieJson: JsonObject = withContext(Dispatchers.Default) { BibleLottieGenerator.generate(config) }
                generatedJson = withContext(Dispatchers.Default) {
                    json.encodeToString(JsonObject.serializer(), lottieJson)
                }
                statusText = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                statusText = Strings.bandStatusError(e.message.orEmpty())
            } catch (e: IllegalArgumentException) {
                statusText = Strings.bandStatusError(e.message.orEmpty())
            }
        }
    }

    companion object {
        private const val DEFAULT_FILE_STEM = "bible-band"

        fun suggestedFileName(cfg: BibleLottieGenConfig): String =
            "$DEFAULT_FILE_STEM-${cfg.bandStyle.name.lowercase()}-${cfg.entrance.name.lowercase()}-" +
                cfg.textAnimation.name.lowercase()
    }
}
