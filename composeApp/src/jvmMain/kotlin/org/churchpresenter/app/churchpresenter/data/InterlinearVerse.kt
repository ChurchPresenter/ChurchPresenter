package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InterlinearWord(
    @SerialName("t") val text: String,
    @SerialName("s") val strongsNumber: String,
)

@Serializable
data class InterlinearVerse(
    @SerialName("r") val ref: String,
    @SerialName("w") val words: List<InterlinearWord>,
) {
    val bookId: Int      get() = ref.substring(0, 3).toIntOrNull() ?: 0
    val chapter: Int     get() = ref.substring(3, 6).toIntOrNull() ?: 0
    val verseNumber: Int get() = ref.substring(6, 9).toIntOrNull() ?: 0
}
