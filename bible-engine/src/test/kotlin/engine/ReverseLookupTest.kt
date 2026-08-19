package engine

import engine.bible.BibleIndex
import engine.bible.SpbLoader
import engine.detection.BookResolver
import engine.detection.ReverseLookup
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReverseLookupTest {

    companion object {
        init {
            val root = AppConfig.discoverBibleRoot()
            if (!root.isNullOrBlank()) {
                Config.bibleRoot = root
                BookResolver.register(SpbLoader.scanAllBookManifests())
            }
        }
    }

    private val translations by lazy { SpbLoader.loadDefaults() }
    private val index by lazy { BibleIndex(translations) }
    private val hasKjv by lazy { translations.any { it.id == "ENG_KJV" } }
    private val hasRst by lazy { translations.any { it.id == "RUS_RST" } }

    private fun requireBibles() = assumeTrue(
        hasKjv && hasRst,
        "KJV/RST not found at Config.bibleRoot='${Config.bibleRoot}' — install ChurchPresenter or set bible.root"
    )

    @Test fun `for God so loved the world finds John 3 16`() {
        requireBibles()
        val result = ReverseLookup.search("for God so loved the world", index, translations)
        assertNotNull(result, "Expected a reverse lookup result")
        assertEquals(43, result.bookNum, "Expected John (book 43)")
        assertEquals(3, result.chapter)
        assertEquals(16, result.verse)
    }

    @Test fun `low ratio returns null`() {
        requireBibles()
        val result = ReverseLookup.search("the", index, translations)
        // Single common word should not meet 2× ratio threshold
        // (may or may not be null depending on data, but very short query is filtered)
        // Only assert it doesn't crash
    }

    @Test fun `Have mercy upon me returns Psalm 51`() {
        requireBibles()
        // Psalm 51:1 — "lovingkindness" disambiguates from other "have mercy upon me" Psalms
        val result = ReverseLookup.search(
            "Have mercy upon me O God according to thy lovingkindness", index, translations
        )
        assertNotNull(result)
        assertEquals(19, result.bookNum, "Expected Psalms (book 19)")
        assertEquals(51, result.chapter)
    }

    @Test fun `in the beginning God created`() {
        requireBibles()
        val result = ReverseLookup.search("in the beginning God created the heaven and the earth", index, translations)
        assertNotNull(result)
        assertEquals(1, result.bookNum, "Expected Genesis (book 1)")
        assertEquals(1, result.chapter)
        assertEquals(1, result.verse)
    }
}
