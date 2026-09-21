package org.churchpresenter.lottiegen.band

/** One text slot as the generator sees it: where it goes, what sample it shows, how big. */
internal class TextSlot(
    val name: String,
    val box: SlotBox,
    val sampleText: String,
    val fontSizePx: Double,
    val color: String,
    val isReference: Boolean = false,
)
