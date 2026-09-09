package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.utils.Constants

@Serializable
data class QASettings(
    val textColor: String = "#FFFFFF",
    val backgroundColor: String = "#1E1E2E",
    val backgroundOpacity: Int = 100,
    val fontSize: Int = 48,
    val fontType: String = "Arial",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 78,
    /** The band behind each line and the box around the block. */
    val backdrop: TextBackdrop = TextBackdrop(),
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.BOTTOM_CENTER,
    val rateLimitCooldownSeconds: Int = 30,
    val votingEnabled: Boolean = false,
    val qrCodeMessage: String = "",
    val qrForegroundColor: String = "#000000",
    val qrBackgroundColor: String = "#FFFFFF",
    val qrBackgroundOpacity: Int = 100,
)
