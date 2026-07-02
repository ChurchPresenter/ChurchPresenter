package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class PresentationRemoteSettings(
    val remoteControlEnabled: Boolean = false,
    val remotePassword: String = ""
)
