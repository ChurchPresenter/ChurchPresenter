package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable

@Serializable
data class StrongsEntry(
    val number: String,
    val word: String,
    val transliteration: String,
    val pronunciation: String,
    val definition: String,
    val kjvUsage: String = ""
) {
    val isHebrew: Boolean get() = number.startsWith("H")
    val isGreek: Boolean get() = number.startsWith("G")
    val numericValue: Int get() = number.drop(1).toIntOrNull() ?: 0
}
