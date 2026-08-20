package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class LottiePreset(
    val id: String = "",
    val label: String = "",
    val savedFileName: String = "",
    val searchReplacePairs: List<LottieSearchReplacePair> = emptyList(),
    val pauseFrame: Float = -1f  // -1 = no pause frame set
)
