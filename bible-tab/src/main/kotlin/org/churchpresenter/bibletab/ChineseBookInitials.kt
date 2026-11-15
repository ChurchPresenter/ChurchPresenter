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
}
