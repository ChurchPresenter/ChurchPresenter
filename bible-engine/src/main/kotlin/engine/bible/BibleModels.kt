package engine.bible

data class EngineVerse(
    val code: String,
    val bookNum: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val isHeader: Boolean,
)

data class EngineBook(
    val num: Int,
    val name: String,
    val chapterCount: Int,
)

/** Dominant writing system of a translation's verse text — content-derived at load time
 *  (SPB headers carry no reliable language field; ids/languages are filename-derived junk
 *  like "KJV1769.SPB"), so translation display selection can match the spoken track's script
 *  instead of trusting hardcoded ids. */
enum class Script { LATIN, CYRILLIC, OTHER }

data class EngineTranslation(
    val id: String,
    val title: String,
    val abbreviation: String,
    val language: String,
    val numbering: String,
    val script: Script = Script.OTHER,
    val books: List<EngineBook>,
    val byBCV: Map<Triple<Int, Int, Int>, EngineVerse>,
    val byChapter: Map<Pair<Int, Int>, List<EngineVerse>>,
    val byCode: Map<String, EngineVerse>,
) {
    fun lookupVerse(bookNum: Int, chapter: Int, verse: Int): EngineVerse? =
        byBCV[Triple(bookNum, chapter, verse)]

    fun firstContentVerse(bookNum: Int, chapter: Int): EngineVerse? =
        byChapter[Pair(bookNum, chapter)]
            ?.filter { !it.isHeader }
            ?.minByOrNull { it.verse }

    fun nextVerse(verse: EngineVerse): EngineVerse? {
        val chapterVerses = byChapter[Pair(verse.bookNum, verse.chapter)]
            ?: return null
        val sorted = chapterVerses.filter { !it.isHeader }.sortedBy { it.verse }
        val idx = sorted.indexOfFirst { it.verse == verse.verse }
        return if (idx >= 0 && idx + 1 < sorted.size) sorted[idx + 1]
        else {
            // Try first verse of next chapter
            val nextChapter = verse.chapter + 1
            firstContentVerse(verse.bookNum, nextChapter)
        }
    }

    fun bookName(bookNum: Int): String =
        books.find { it.num == bookNum }?.name ?: "Unknown"
}
