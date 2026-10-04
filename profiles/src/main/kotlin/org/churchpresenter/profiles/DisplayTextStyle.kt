package org.churchpresenter.profiles

import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline

/**
 * The text look captions, Q&A questions and subtitles share, field for field -- the same twelve
 * settings on their settings classes -- so the Profiles tab draws one set of rows for all three
 * (`DisplayTextRows`).
 */
internal data class DisplayTextStyle(
    val textColor: String,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val shadow: Boolean,
    val shadowColor: String,
    val shadowSize: Int,
    val shadowOpacity: Int,
    val backdrop: TextBackdrop,
    val outline: TextOutline,
    val fontType: String,
    val fontSize: Int,
)
