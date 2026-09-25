package org.churchpresenter.app.churchpresenter.utils

/**
 * Which kind of content a tab's scale button sets.
 *
 * The Pictures and Media tabs draw the same three-mode button, and their tooltips used to read the
 * same sentence too -- "Scale: Fit" -- though one governs photos and the other video. This is what
 * lets `scaleButtonLabel` name the right one.
 */
internal enum class ScaleButtonContent { PICTURES, MEDIA }
