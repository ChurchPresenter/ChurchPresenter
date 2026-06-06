package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

@Serializable
data class STTSettings(
    val serverUrl: String = "http://localhost:80",
    val displayMode: String = "transcribe", // "transcribe", "translate", "both"
    val layout: String = "stacked", // "stacked", "stacked_inverse", "side_by_side", "side_by_side_inverse"
    val showWordHighlighting: Boolean = false,
    val maxSegments: Int = 5, // last N segments shown (0 = unlimited) — used for tab preview
    val maxLines: Int = 3, // max visible lines on projection display (0 = unlimited)
    val showInProgress: Boolean = false,
    val showTranslationInProgress: Boolean = false,
    val dripFeedEnabled: Boolean = true,
    val dripFeedSpeed: Int = 140, // ms per word
    val textColor: String = "#FFFFFF",
    val translationTextColor: String = "#FFFFFF",
    val backgroundColor: String = "transparent",
    val backgroundOpacity: Int = 0,
    val fontSize: Int = 42,
    val lineSpacing: Int = 130, // line height as percentage of font size (100 = no extra spacing)
    val fontType: String = "Arial",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 78,
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.BOTTOM,
)
