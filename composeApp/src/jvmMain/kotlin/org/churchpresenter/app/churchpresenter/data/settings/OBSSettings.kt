package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class OBSSettings(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = 4455,
    val password: String = "",
    val defaultScene: String = "",
    val sceneMappings: Map<String, String> = emptyMap()
)
