package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class AtemSettings(
    val host: String = "",
    val port: Int = 9910,
    val defaultStillSlot: Int = 0,
    val defaultClipSlot: Int = 0,
    val renderWidth: Int = 1920,
    val renderHeight: Int = 1080,
    /** Exact frame rate, e.g. 59.94 — fractional NTSC rates must not be truncated. */
    val clipFps: Double = 30.0,
    /** Slot counts detected on the last successful test connection; 0 = unknown. */
    val detectedStillSlots: Int = 0,
    val detectedClipSlots: Int = 0,
    /** Frame capacity per clip bank from the last test connection; empty = unknown. */
    val detectedClipMaxFrames: List<Int> = emptyList(),
    /** Media pool frames not allocated to any clip bank, from the last test connection. */
    val detectedUnassignedFrames: Int = 0,
    /** Skip the upload dialog: one-press upload straight to the default slots. */
    val quickUpload: Boolean = false,
    /** Default downstream keyer for the Companion lower-third sequence (0-based; shown 1-based). */
    val dskIndex: Int = 0,
    /** Delay between DSK on-air and starting the animation (lets the cut settle). */
    val dskPreRollMs: Int = 300,
    /** Delay between the animation ending and DSK off-air. */
    val dskPostRollMs: Int = 300,
    /** Downstream keyer count from the last test connection; 0 = unknown. */
    val detectedDskCount: Int = 0,
    /** Go Live in the Lower Third tab also runs the timed DSK sequence. */
    val goLiveDsk: Boolean = false
)
