package org.churchpresenter.converter.song

/**
 * Makes a song export parseable when the app that wrote it did not escape its own text.
 *
 * This is not a hypothetical: a Quelea pack of 3,134 English songs has 40 entries an XML parser
 * rejects outright — a bare `&` between two songwriters' names, and lyrics containing a literal
 * `<<>>` — and OpenLP carries the same recovery for EasySlides. Without it those songs do not
 * import badly, they do not import at all, and a church would find out by noticing something
 * missing mid-service.
 *
 * Only text that cannot be markup is escaped: an `&` that starts no entity, and a `<` that starts
 * no tag, comment or processing instruction. Anything that could be a real tag is left alone, so a
 * well-formed document passes through this unchanged.
 */

private val bareAmpersand = Regex("""&(?!#\d+;|#x[0-9a-fA-F]+;|[a-zA-Z][a-zA-Z0-9.\-_]*;)""")
private val strayLessThan = Regex("""<(?![a-zA-Z/?!])""")

internal fun repairXml(text: String): String =
    text.replace(bareAmpersand, "&amp;").replace(strayLessThan, "&lt;")
