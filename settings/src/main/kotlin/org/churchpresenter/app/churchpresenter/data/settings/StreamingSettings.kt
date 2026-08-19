package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class StreamingSettings(
    val lowerThirdFolder: String = "",
    val savedSearchStrings: List<String> = emptyList(),
    val savedReplaceStrings: List<String> = emptyList(),
    val lottiePresets: List<LottiePreset> = emptyList(),
    val windowTop: Int = 0,
    val windowLeft: Int = 0,
    val windowRight: Int = 0,
    val windowBottom: Int = 0,
    val lowerThirdListWidthDp: Int = 240
)
