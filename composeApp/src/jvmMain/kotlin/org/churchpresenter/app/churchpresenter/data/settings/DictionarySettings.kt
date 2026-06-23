package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class DictionarySettings(
    // Original word (Hebrew/Greek text)
    val wordColor: String = "#FFFFFF",
    val wordFontType: String = "Arial",
    val wordFontSize: Int = 70,
    val wordBold: Boolean = false,
    val wordItalic: Boolean = false,
    val wordShadow: Boolean = false,
    val wordShadowColor: String = "#000000",
    val wordShadowSize: Int = 100,
    val wordShadowOpacity: Int = 90,

    // Reference text (Strong's number, transliteration, pronunciation, KJV usage)
    val referenceColor: String = "#FFFFFF",
    val referenceFontType: String = "Arial",
    val referenceFontSize: Int = 28,
    val referenceShadow: Boolean = false,
    val referenceShadowColor: String = "#000000",
    val referenceShadowSize: Int = 100,
    val referenceShadowOpacity: Int = 90,

    // Definition
    val definitionColor: String = "#DDDDDD",
    val definitionFontSize: Int = 32,

    // KJV usage
    val kjvUsageColor: String = "#AAAAAA",
    val kjvUsageFontSize: Int = 22,

    // Card overlay background
    val cardBackgroundColor: String = "#1A1A2E",
    val cardBackgroundOpacity: Float = 0.92f,

    // Transitions
    val fadeIn: Boolean = true,
    val fadeOut: Boolean = true,
    val transitionDuration: Float = 500f,

    // Visible fields on presenter output
    val showWord: Boolean = true,
    val showReference: Boolean = true,
    val showDefinition: Boolean = true,
    val showKjvUsage: Boolean = true,
)
