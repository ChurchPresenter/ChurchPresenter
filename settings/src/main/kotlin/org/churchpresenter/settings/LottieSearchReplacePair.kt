package org.churchpresenter.settings

import kotlinx.serialization.Serializable

@Serializable
data class LottieSearchReplacePair(
    val search: String = "",
    val replace: String = ""
)
