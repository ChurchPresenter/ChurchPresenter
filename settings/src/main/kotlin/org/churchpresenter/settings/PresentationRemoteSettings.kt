package org.churchpresenter.settings

import kotlinx.serialization.Serializable

@Serializable
data class PresentationRemoteSettings(
    val remoteControlEnabled: Boolean = false
)
