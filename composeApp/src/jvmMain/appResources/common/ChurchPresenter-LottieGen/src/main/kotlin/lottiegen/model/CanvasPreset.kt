package lottiegen.model

import lottiegen.ui.Strings

data class CanvasPreset(val label: String, val width: Int, val height: Int)

val CANVAS_PRESETS = listOf(
    CanvasPreset("16:9", 1920, 1080),
    CanvasPreset("4:3", 1440, 1080),
    CanvasPreset("16:10", 1728, 1080),
    CanvasPreset("21:9", 2560, 1080),
    CanvasPreset("1:1", 1080, 1080),
    CanvasPreset("9:16", 1080, 1920)
)

data class TimingPreset(val labelKey: String, val animDuration: Float, val holdDuration: Float) {
    val label: String get() = when (labelKey) {
        "fast" -> Strings.timingFast
        "medium" -> Strings.timingMedium
        "slow" -> Strings.timingSlow
        else -> labelKey
    }
}

val TIMING_PRESETS = listOf(
    TimingPreset("fast", 2f, 2f),
    TimingPreset("medium", 4f, 3f),
    TimingPreset("slow", 6f, 5f)
)
