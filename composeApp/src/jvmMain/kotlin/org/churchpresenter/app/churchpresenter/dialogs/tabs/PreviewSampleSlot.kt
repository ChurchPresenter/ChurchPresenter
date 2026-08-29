package org.churchpresenter.app.churchpresenter.dialogs.tabs

/**
 * Which of the three sample lengths a preview is drawing.
 *
 * One fixed sample says nothing about auto-fit: the question an operator is actually asking of this
 * preview is whether a two-word verse blows up past the margins and whether a long one still fits
 * the band, and neither is visible without something of each length to switch between.
 */
internal enum class PreviewSampleSlot { SHORT, MEDIUM, LONG }
