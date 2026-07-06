package org.churchpresenter.app.churchpresenter.models

import androidx.compose.ui.graphics.ImageBitmap

/** One button on a Companion Satellite surface, as last reported by Companion. */
data class CompanionButtonState(
    val index: Int,
    val bitmap: ImageBitmap? = null,
    val text: String = "",
    val color: String? = null,
    val textColor: String? = null,
    val pressed: Boolean = false
)
