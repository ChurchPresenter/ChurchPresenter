package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class WebBookmark(
    val url: String = "",
    val title: String = ""
)
