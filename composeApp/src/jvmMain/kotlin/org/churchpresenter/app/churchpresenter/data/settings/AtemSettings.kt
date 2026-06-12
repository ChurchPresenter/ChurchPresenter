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
    val detectedClipSlots: Int = 0
)
