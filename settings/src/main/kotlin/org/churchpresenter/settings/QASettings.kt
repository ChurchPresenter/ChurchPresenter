package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
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
    /** The stroke drawn around the glyphs, under the fill. */
    val outline: TextOutline = TextOutline(),
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.BOTTOM_CENTER,
    val rateLimitCooldownSeconds: Int = 30,
    val votingEnabled: Boolean = false,
    val qrCodeMessage: String = "",
    val qrForegroundColor: String = "#000000",
    val qrBackgroundColor: String = "#FFFFFF",
    val qrBackgroundOpacity: Int = 100,
    /**
     * Each item's own text box, keyed by [textBoxKey] with [QA_QUESTION_BOX], [QA_QR_CODE_BOX] or [QA_QR_MESSAGE_BOX].
     * An item with none, or with one turned off, is drawn where it always was.
     */
    val textBoxes: Map<String, TextBox> = emptyMap(),
    /** How this page's boxes behave -- see [TextBoxOptions]. */
    val textBoxOptions: TextBoxOptions = TextBoxOptions(),
)
