package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter

enum class ButtonHierarchy { Primary, Secondary, Tertiary }

enum class ButtonIntent { Default, Critical, Neutral }

enum class IconButtonSize { Large, Medium, Small }

enum class FabSize { Regular, Small }

enum class SegmentedButtonSize { Default, Small }

enum class TextStyleToggle { Bold, Italic, Underline, Strikethrough }

class SegmentedOption(
    val label: String,
    val icon: Painter? = null,
    val iconOnly: Boolean = false,
    val enabled: Boolean = true,
)
