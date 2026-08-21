package org.churchpresenter.settings

import kotlinx.serialization.Serializable

@Serializable
data class WebBookmark(
    val url: String = "",
    val title: String = ""
)
