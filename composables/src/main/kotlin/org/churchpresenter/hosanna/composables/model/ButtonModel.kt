package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter

/**
 * How much a button stands out: [Primary] is filled, [Secondary] is tonal and [Tertiary] is text only.
 */
enum class ButtonHierarchy { Primary, Secondary, Tertiary }

/**
 * What a button does, which picks its colors: [Default] (primary), [Critical] (error), [Neutral] (secondary),
 * [Success] (success) and [Warning] (tertiary).
 */
enum class ButtonIntent { Default, Critical, Neutral, Success, Warning }

/**
 * The side of an icon or circular button: [Large], [Medium] or [Small].
 */
enum class IconButtonSize { Large, Medium, Small }

/**
 * The size of a floating action button: [Regular] or [Small].
 */
enum class FabSize { Regular, Small }

/**
 * The height of a segmented button: [Default] or [Small].
 */
enum class SegmentedButtonSize { Default, Small }

/**
 * A font style that can be switched on and off: [Bold], [Italic], [Underline] and [Strikethrough].
 */
enum class TextStyleToggle { Bold, Italic, Underline, Strikethrough }

/**
 * One segment of a [org.churchpresenter.hosanna.composables.button.SegmentedButton].
 *
 * @property label the segment's text, also its name for screen readers when [iconOnly] is set.
 * @property icon an optional icon before the label.
 * @property iconOnly shows only the icon.
 * @property enabled whether the segment can be picked.
 */
class SegmentedOption(
    val label: String,
    val icon: Painter? = null,
    val iconOnly: Boolean = false,
    val enabled: Boolean = true,
)
