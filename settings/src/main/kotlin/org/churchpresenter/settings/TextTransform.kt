package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants

/**
 * The re-casings a settings picker offers, in the order it offers them.
 *
 * Here rather than beside the presenters that apply them because the Customize dialog builds its
 * picker from this list and the settings module is what both sides already share. Applying a
 * transform to text is `applyTextTransform`, in the app's own `TextStyling`.
 */
fun textTransformOptions(): List<String> = listOf(
    Constants.TEXT_TRANSFORM_NONE,
    Constants.TEXT_TRANSFORM_UPPERCASE,
    Constants.TEXT_TRANSFORM_LOWERCASE,
    Constants.TEXT_TRANSFORM_CAPITALIZE,
)
