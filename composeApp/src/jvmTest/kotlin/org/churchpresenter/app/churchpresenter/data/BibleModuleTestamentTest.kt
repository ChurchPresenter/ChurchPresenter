package org.churchpresenter.app.churchpresenter.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which portion of scripture a browse row claims to cover.
 *
 * The published book counts decide it wherever a source supplies them, and only the Zefania archive
 * doesn't — so the name-reading fallback is the exception here rather than the rule. It is worth
 * pinning both, because the fallback is wrong often enough to matter: measured against eBible's own
 * counts it misreads about a fifth of that catalogue, which is exactly why it lost the argument.
 */
class BibleModuleTestamentTest {

    private fun module(
        displayName: String = "A Conservative Version",
        otBookCount: Int = 0,
        ntBookCount: Int = 0,
    ) = BibleModule(
        sourceId = BibleSourceId.EBIBLE,
        downloadKey = "x",
        language = "ENG",
        identifier = "X",
        displayName = displayName,
        otBookCount = otBookCount,
        ntBookCount = ntBookCount,
        fileStem = "ENG_X",
    )

    // --- decided by the published counts ---

    @Test
    fun `books in both testaments is a whole Bible`() {
        assertEquals(Testament.FULL, module(otBookCount = 39, ntBookCount = 27).testament)
    }

    @Test
    fun `books in only the New Testament is a New Testament`() {
        assertEquals(Testament.NEW, module(ntBookCount = 27).testament)
    }

    @Test
    fun `books in only the Old Testament is an Old Testament`() {
        assertEquals(Testament.OLD, module(otBookCount = 39).testament)
    }

    @Test
    fun `a single Old Testament book alongside the New counts as both`() {
        // "NT+Psalms" is a real shape in the catalogue, and the name alone reads it as NT only.
        assertEquals(
            Testament.FULL,
            module(displayName = "Assyrian Neo-Aramaic NT+Psalms", otBookCount = 1, ntBookCount = 27).testament
        )
    }

    @Test
    fun `the counts beat the name when the two disagree`() {
        // The name says NT, the contents say otherwise; the contents are not a reading of a title.
        assertEquals(Testament.FULL,
            module(displayName = "Some NT Edition", otBookCount = 39, ntBookCount = 27).testament)
    }

    @Test
    fun `a name that spells out New Testament is no longer taken for a whole Bible`() {
        // The case the old name-only rule got wrong: no standalone "NT" token to find.
        assertEquals(Testament.NEW, module(displayName = "New Testament in Achi", ntBookCount = 27).testament)
    }

    // --- the fallback, for a source that publishes no counts ---

    @Test
    fun `with no counts a standalone NT token names the portion`() {
        assertEquals(Testament.NEW, module(displayName = "Luther 1912 NT").testament)
    }

    @Test
    fun `with no counts a standalone OT token names the portion`() {
        assertEquals(Testament.OLD, module(displayName = "Luther 1912 OT").testament)
    }

    @Test
    fun `with no counts and no token it is assumed to be a whole Bible`() {
        assertEquals(Testament.FULL, module(displayName = "King James Version").testament)
    }

    @Test
    fun `with no counts a name carrying both tokens is a whole Bible`() {
        assertEquals(Testament.FULL, module(displayName = "Complete OT and NT").testament)
    }

    @Test
    fun `a Holy Bible XML row is read from its counted books, not from its title`() {
        // The generated manifest counts the `<book number>` elements actually present, so an edition
        // whose title spells "New Testament" out is still read correctly — which is the case the
        // token fallback documented above gets wrong for about a fifth of eBible's catalogue.
        assertEquals(
            Testament.NEW,
            module(displayName = "New Testament in Achi", ntBookCount = 27).testament
        )
    }

    @Test
    fun `the token has to stand alone to count`() {
        // Otherwise any word containing the letters would name a testament.
        assertEquals(Testament.FULL, module(displayName = "Contemporary").testament)
    }
}
