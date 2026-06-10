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
    val clipFps: Int = 30
)
