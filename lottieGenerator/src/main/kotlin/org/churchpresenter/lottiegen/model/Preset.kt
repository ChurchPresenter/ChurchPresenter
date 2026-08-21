package org.churchpresenter.lottiegen.model

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val name: String,
    val savedAt: String,
    val config: LottieGenConfig
)
