package org.churchpresenter.app.churchpresenter.dialogs.tabs

/**
 * The Bible page's "All" chip: the stack's All layer, which every translation follows except where
 * it has a value of its own -- see `BibleAllLayer.kt`. An edit under All reaches each translation
 * that has not set that field for itself; one made with a translation picked becomes its own.
 */
internal const val ALL_TRANSLATIONS = -1

/** [translationIndex] as the pane will use it: All only means something with two or more to style. */
internal fun effectiveTranslationIndex(translationIndex: Int, stackSize: Int): Int = when {
    translationIndex == ALL_TRANSLATIONS && stackSize > 1 -> ALL_TRANSLATIONS
    else -> translationIndex.coerceIn(0, (stackSize - 1).coerceAtLeast(0))
}
