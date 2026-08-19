package org.churchpresenter.app.churchpresenter.utils

/**
 * Any line wrapped in [] or {} is a section header — except one holding nothing but a chord, which
 * an instrumental break writes as its own line. See [ChordTransposer.isSectionHeader].
 */
fun isHeaderLine(line: String): Boolean = ChordTransposer.isSectionHeader(line)

/** {} = chorus, [] = verse/other */
fun isChorusHeader(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("{") && t.endsWith("}")
}

