package org.churchpresenter.ui

/**
 * One installed family, as the picker needs to describe it.
 *
 * [cyrillic] and [hebrew] are measured — the family either has the glyphs or it does not, and a
 * verse set in one that does not comes out of the fallback font instead. [category] and
 * [recommended] cannot be measured (nothing in a font file says "this reads well at 70pt across a
 * hall"), so they come from the tables in [FontCatalog].
 */
data class FontFace(
    val name: String,
    val category: FontCategory,
    val cyrillic: Boolean,
    val hebrew: Boolean,
    val recommended: Boolean,
)
