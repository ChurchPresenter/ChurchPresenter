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
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.ui.PreviewGuide
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

    /** Whether the design on screen is the one last written, until the next change to it. */
    var isSaved by mutableStateOf(false)
        private set
    var fileName by mutableStateOf(suggestedFileName(config))
        private set

    /** Whether the preview outlines the text slots. A preview aid only: it never reaches the file. */
    var showSlotGuides by mutableStateOf(true)

    /** Whether one text-area margin slider moves all four. A pane setting, not part of the file. */
    var linkTextArea by mutableStateOf(false)

    /** All four text-area margins at once, for the linked sliders. */
    fun setTextArea(px: Int) {
        updateConfig { it.copy(textAreaLeftPx = px, textAreaRightPx = px, textAreaTopPx = px, textAreaBottomPx = px) }
    }

    /** The part of the timeline the text is on screen for, as fractions: the outlines show then. */
    val textWindow: ClosedFloatingPointRange<Float>
        get() {
            val t = timeline
            return t.textStart.toFloat() / t.totalFrames..t.bgOutStart.toFloat() / t.totalFrames
        }

    /** The text slots as fractions of the canvas, for the preview to outline; empty when hidden. */
    val slotGuides: List<PreviewGuide>
        get() {
            if (!showSlotGuides) return emptyList()
            val slots = computeSlots(config)
            return listOfNotNull(slots.text1, slots.reference1, slots.text2, slots.reference2)
                .map { it.toGuide(config.canvasW, config.canvasH) }
        }

    val timeline: BandTimeline get() = BandTimeline.from(config)

    private var generateJob: Job? = null
    private val json = Json { prettyPrint = true }

    init {
        scheduleGenerate()
    }

    fun updateConfig(transform: (BibleLottieGenConfig) -> BibleLottieGenConfig) {
        val before = config
        config = transform(config)
        isSaved = false
        if (fileName == suggestedFileName(before)) fileName = suggestedFileName(config)
        scheduleGenerate()
    }

    /**
     * One small band per style in the current colours, for the template menu to show what each
     * style actually is. Generated and parsed off the UI thread the first time the menu asks, and
     * again only when a colour changes: the sample text and pictures are left out, so a thumbnail
     * is cheap — and parsed here rather than in the menu, so it draws on the frame it arrives.
     */
    var styleThumbnails by mutableStateOf<Map<BandStyle, LottieComposition>>(emptyMap())
        private set

    /** The timeline the thumbnails were built on, so the menu can seek to their hold frame. */
    val thumbnailTimeline: BandTimeline get() = BandTimeline.from(thumbnailConfig(config))

    private var thumbnailKey: BibleLottieGenConfig? = null

    fun ensureStyleThumbnails() {
        val key = thumbnailConfig(config)
        if (key == thumbnailKey) return
        thumbnailKey = key
        scope.launch {
            val built = withContext(Dispatchers.Default) {
                BandStyle.entries.associateWith { style ->
                    val lottie = BibleLottieGenerator.generate(key.copy(bandStyle = style))
                    LottieCompositionSpec.JsonString(Json.encodeToString(JsonObject.serializer(), lottie)).load()
                }
            }
            if (thumbnailKey == key) styleThumbnails = built
        }
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

    fun updateLook(role: BandColorRole, transform: (BandRoleLook) -> BandRoleLook) {
        updateConfig { it.copy(looks = it.looks + (role to transform(it.look(role)))) }
    }

    /** Whether a picture chooser is up: the popover that opened it keeps still until it closes. */
    var choosingImage by mutableStateOf(false)
        private set

    /**
     * Opens [chooser] for [role]'s picture on this scope rather than the caller's: the popover
     * that asks loses focus to the native dialog and can leave composition while it is up, and a
     * pick cancelled half-way is what left a stray picture behind.
     */
    fun chooseBandImage(role: BandColorRole, chooser: suspend () -> File?) {
        if (choosingImage) return
        choosingImage = true
        scope.launch {
            try {
                chooser()?.let { loadBandImage(role, it) }
            } finally {
                choosingImage = false
            }
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
            isSaved = true
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

        private fun SlotBox.toGuide(canvasW: Int, canvasH: Int) = PreviewGuide(
            left = (x / canvasW).toFloat(),
            top = (y / canvasH).toFloat(),
            width = (w / canvasW).toFloat(),
            height = (h / canvasH).toFloat(),
        )

        fun suggestedFileName(cfg: BibleLottieGenConfig): String =
            "$DEFAULT_FILE_STEM-${cfg.bandStyle.name.lowercase()}-${cfg.entrance.name.lowercase()}-" +
                cfg.textAnimation.name.lowercase()
    }
}

/** The design without its words and pictures: what a thumbnail is made from, and what it is keyed on. */
private fun thumbnailConfig(cfg: BibleLottieGenConfig): BibleLottieGenConfig = cfg.copy(
    previewText1 = "", previewReference1 = "", previewText2 = "", previewReference2 = "",
    images = emptyMap(),
)
