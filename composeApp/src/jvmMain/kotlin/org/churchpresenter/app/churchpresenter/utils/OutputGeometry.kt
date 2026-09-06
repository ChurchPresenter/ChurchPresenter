package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.ScreenAssignment

/** Standing in for an output whose real size nothing has reported yet. */
private const val FALLBACK_OUTPUT_WIDTH = 1920
private const val FALLBACK_OUTPUT_HEIGHT = 1080

/**
 * Which of the three lists in `ProjectionSettings` an assignment came from.
 *
 * It matters because the three keep their size in different fields: a physical screen reports
 * `targetBoundsW/H`, while a Browser Source and an NDI output are configured to a resolution of
 * their own and leave those two at zero. An assignment on its own cannot say which it is: it
 * carries all three pairs of fields, and nothing on it distinguishes a Browser Source from an NDI
 * output.
 */
enum class OutputKind { SCREEN, BROWSER_SOURCE, NDI }

/**
 * The pixel size of an output, and the shape a preview of it should be.
 *
 * The one definition of "how big is this output", so a preview cannot disagree with the screen it
 * stands for. [aspectRatio] is what a preview wants; [width] and [height] are what the operator is
 * shown when the numbers themselves matter.
 */
data class OutputSize(val width: Int, val height: Int) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

/** One output a tab could be previewing: its stored identity, what to call it, and how big it is. */
data class PreviewOutput(val key: String, val label: String, val size: OutputSize)

/** 1920x1080 -- what an output whose size is not known yet is previewed as. */
val FallbackOutputSize = OutputSize(FALLBACK_OUTPUT_WIDTH, FALLBACK_OUTPUT_HEIGHT)

/** [FallbackOutputSize]'s shape, for the few previews that cannot reach the settings to ask. */
val FALLBACK_STAGE_ASPECT: Float = FallbackOutputSize.aspectRatio

/**
 * The resolutions an output can be set to, offered by every card that has one.
 *
 * Deliberately not 16:9 only, which is all the Browser Source and NDI lists used to hold: a church
 * runs 4:3 projectors, 16:10 panels, ultrawides and portrait confidence displays, and an output
 * that cannot be set to their shape cannot be used to check what will land on them. Anything not
 * here is reachable through the custom entry.
 */
val OUTPUT_RESOLUTIONS: List<OutputSize> = listOf(
    OutputSize(1280, 720),
    OutputSize(1920, 1080),
    OutputSize(2560, 1440),
    OutputSize(3840, 2160),
    OutputSize(1024, 768),
    OutputSize(1600, 1200),
    OutputSize(1920, 1200),
    OutputSize(2560, 1080),
    OutputSize(3440, 1440),
    OutputSize(1080, 1920),
)

/**
 * How big [assignment] is, given which list it came from.
 *
 * A physical screen prefers its stored bounds and asks the live display only when those are unset,
 * so the answer is stable on a headless machine and while a monitor is being re-plugged. A virtual
 * output is whatever it was configured to be -- there is no hardware to ask.
 */
fun outputSizeOf(assignment: ScreenAssignment, kind: OutputKind): OutputSize = when (kind) {
    OutputKind.BROWSER_SOURCE -> sizeOrFallback(assignment.browserSourceWidth, assignment.browserSourceHeight)
    OutputKind.NDI -> sizeOrFallback(assignment.ndiWidth, assignment.ndiHeight)
    OutputKind.SCREEN ->
        if (assignment.targetBoundsW > 0 && assignment.targetBoundsH > 0) {
            OutputSize(assignment.targetBoundsW, assignment.targetBoundsH)
        } else {
            // No stored bounds means no display behind this slot -- a dev fallback window, or an
            // output set to None. Its configured size is the answer; asking the live display
            // instead returned the PRIMARY monitor's shape, which is not where anything goes.
            sizeOrFallback(assignment.devWindowWidth, assignment.devWindowHeight)
        }
}

private fun sizeOrFallback(width: Int, height: Int): OutputSize =
    if (width > 0 && height > 0) OutputSize(width, height) else FallbackOutputSize
