package org.churchpresenter.bibletab

/** Pinyin initials in canonical book order, independent of the Bible module's language. */
internal object ChineseBookInitials {
    private val aliases = listOf(
        "csj", "caj", "lwj", "msj", "smj", "ysyj", "ssj", "ldj",
        "smejs", "smejx",
        "lwjs", "lwjx", "ldzs", "ldzx",
        "yslj", "nxmj", "ystj", "ybj", "sp", "zy", "cds", "yg",
        "ysys", "ylms", "ylag", "yxjs", "dyls", "hxs", "yes", "ams",
        "ebdys", "yns", "mjs", "nhs", "hbgs", "xfys", "hgs", "sjlys", "mljs",
        "mtfy", "mkfy", "ljfy", "yhfy", "stxz", "lms", "gldqs", "gldhs",
        "jlts", "yfss", "flbs", "glxs", "tsljqs", "tsljhs",
        "tmtqs", "tmths", "tds", "flms", "xbls", "ygs", "bdqs", "bdhs",
        "yhys", "yhes", "yhss", "yds", "qsl",
    ).withIndex().associate { (index, initials) -> initials to index + 1 }

    fun bookId(token: String): Int? = aliases[token.trim().lowercase()]

    /** Prefixes rank first; missing letters may match in their original order. */
    fun candidates(token: String): List<Int> {
        val query = token.trim().lowercase()
        if (query.length < 2 || query.any { it !in 'a'..'z' }) return emptyList()
        return aliases.entries.mapNotNull { (initials, id) ->
            var next = 0
            for (letter in initials) {
                if (next < query.length && letter == query[next]) next++
            }
            val rank = when {
                initials == query -> 0
                initials.startsWith(query) -> 1
                next == query.length -> 2
                else -> return@mapNotNull null
            }
            Triple(rank, id, initials.length)
        }.sortedWith(compareBy<Triple<Int, Int, Int>> { it.first }.thenBy { it.third }.thenBy { it.second })
            .map { it.second }
    }
}
