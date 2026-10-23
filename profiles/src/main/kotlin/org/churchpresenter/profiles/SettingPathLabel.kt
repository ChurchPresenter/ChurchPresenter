package org.churchpresenter.profiles

/**
 * A setting's name read off its path: `bibleSettings.translations[kjv.spb].textFontSize` is
 * "Text font size · KJV", and a Bible box's `bibleSettings.textBoxes[REFERENCE#kjv.spb@LT].yPercent`
 * is "Y percent · Reference · KJV · LT". Named after the stored field, so it reads the way the
 * settings file does rather than the way a row labels it -- the card lists changes on every page,
 * not only this one's.
 */
internal fun settingPathLabel(path: String): String {
    val entry = ENTRY.find(path)?.groupValues?.get(1)?.replace(ESCAPED, "$1")?.let(::entryLabel)
    val leaf = path.replace(ENTRY, "").substringAfterLast('.')
    return if (entry != null) "${words(leaf)} · $entry" else words(leaf)
}

/** A bracketed name as words: a file name without its extension, a box as its item and its translation. */
private fun entryLabel(name: String): String {
    val lowerThird = name.endsWith(LOWER_THIRD_SUFFIX)
    val key = name.removeSuffix(LOWER_THIRD_SUFFIX)
    val item = key.substringBefore('#', missingDelimiterValue = "")
    val file = key.substringAfter('#').substringBeforeLast('.').uppercase()
    return listOfNotNull(
        words(item.lowercase()).takeIf { item.isNotEmpty() },
        file,
        LOWER_THIRD_SUFFIX.removePrefix("@").takeIf { lowerThird },
    ).joinToString(" · ")
}

private fun words(camel: String): String =
    camel.replace(CAMEL_HUMP, " $1").lowercase().replaceFirstChar { it.uppercase() }

/** How a box key names the lower third's box of an item -- `textBoxKey`'s suffix. */
private const val LOWER_THIRD_SUFFIX = "@LT"

/** A name in brackets -- a translation, a Bible box -- with a bracket or backslash in it escaped. */
private val ENTRY = Regex("""\[((?:\\.|[^]\\])*)]""")
private val ESCAPED = Regex("""\\(.)""")
private val CAMEL_HUMP = Regex("(?<=[a-z0-9])([A-Z])")
