package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class LottieSearchReplacePair(
    val search: String = "",
    val replace: String = ""
)
