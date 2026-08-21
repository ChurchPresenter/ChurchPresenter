package org.churchpresenter.settings

import kotlinx.serialization.Serializable

@Serializable
data class StockPhotoSettings(
    val pexelsApiKey: String = "",
    val pixabayApiKey: String = ""
)
