package org.churchpresenter.ui

/** The families the picker offers, plus how many it left out. */
data class FontCatalogSnapshot(
    val faces: List<FontFace>,
    val hiddenCount: Int,
    /** False until the glyph scan has run: every face reads as covering nothing. */
    val measured: Boolean,
)
