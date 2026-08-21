package org.churchpresenter.bibleformats.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The `@Serializable` DTOs the two catalogues decode into, built by hand rather than from JSON.
 *
 * Every field is defaulted so a manifest can gain a column without an app release, which means
 * kotlinx.serialization generates each of these a *second*, masked constructor used only for
 * decoding. The rest of the suite exercises that one — decode a manifest, assert what comes out —
 * and never the plain constructor, its accessors, or the `equals`/`copy` the app relies on when it
 * diffs a refetched catalogue against the cached one.
 *
 * So this pins the ordinary side: a value put in comes back out, two entries differing in one field
 * are not equal, and a defaulted DTO reads as documented. A mis-ordered pair of same-typed
 * parameters — `sha` and `title`, `path` and `type` — compiles and round-trips through JSON without
 * complaint, and would only surface here.
 */
class CatalogDtoConstructionTest {

    // ── Beblia: the manifest's own shape ────────────────────────────────────────

    @Test
    fun `a manifest entry keeps every field it was given`() {
        val entry = BebliaCatalogIndex.Entry(
            file = "EnglishKJBible.xml",
            sha = "abc123",
            size = 4_404_123,
            title = "English KJV",
            id = "KJ",
            lang = "ENG",
            langName = "English",
            langFrom = "filename",
            rights = "Public Domain",
            url = "https://example.invalid/EnglishKJBible.xml",
            ot = 39,
            nt = 27,
        )

        assertEquals("EnglishKJBible.xml", entry.file)
        assertEquals("abc123", entry.sha)
        assertEquals(4_404_123, entry.size)
        assertEquals("English KJV", entry.title)
        assertEquals("KJ", entry.id)
        assertEquals("ENG", entry.lang)
        assertEquals("English", entry.langName)
        assertEquals("filename", entry.langFrom)
        assertEquals("Public Domain", entry.rights)
        assertEquals("https://example.invalid/EnglishKJBible.xml", entry.url)
        assertEquals(39, entry.ot)
        assertEquals(27, entry.nt)
    }

    @Test
    fun `an entry with nothing supplied reads as the documented defaults`() {
        val blank = BebliaCatalogIndex.Entry()

        assertEquals("", blank.file)
        assertEquals("", blank.sha)
        assertEquals(0, blank.size)
        assertEquals(0, blank.ot)
        assertEquals(0, blank.nt)
    }

    /**
     * The cache is compared against a refetch to decide whether anything changed, so two entries
     * that differ anywhere have to be unequal — including in a field the browse row never shows.
     */
    @Test
    fun `entries compare by value, field by field`() {
        val entry = BebliaCatalogIndex.Entry(file = "KJV.xml", sha = "abc", lang = "ENG")

        assertEquals(entry, entry.copy())
        assertEquals(entry.hashCode(), entry.copy().hashCode())
        assertNotEquals(entry, entry.copy(sha = "def"))
        assertNotEquals(entry, entry.copy(langFrom = "override"))
        assertTrue(entry.toString().contains("KJV.xml"), "the file name is what identifies it in a log")
    }

    @Test
    fun `a catalogue file carries its schema version, commit and entries`() {
        val file = BebliaCatalogIndex.CatalogFile(
            schemaVersion = 2,
            commit = "0".repeat(40),
            bibles = listOf(BebliaCatalogIndex.Entry(file = "KJV.xml")),
        )

        assertEquals(2, file.schemaVersion)
        assertEquals("0".repeat(40), file.commit)
        assertEquals(1, file.bibles.size)
        assertNotEquals(file, file.copy(commit = "1".repeat(40)))
    }

    @Test
    fun `an empty catalogue file defaults to schema one and no bibles`() {
        val blank = BebliaCatalogIndex.CatalogFile()

        assertEquals(1, blank.schemaVersion)
        assertEquals("", blank.commit)
        assertTrue(blank.bibles.isEmpty())
    }

    // ── Zefania: the git tree listing ───────────────────────────────────────────

    @Test
    fun `a tree entry keeps its path, type, hash and size`() {
        val entry = ZefaniaRepositoryIndex.TreeEntry(
            path = "bibles/ENG/KJV.zip",
            type = "blob",
            sha = "deadbeef",
            size = 1_234,
        )

        assertEquals("bibles/ENG/KJV.zip", entry.path)
        assertEquals("blob", entry.type)
        assertEquals("deadbeef", entry.sha)
        assertEquals(1_234, entry.size)
    }

    /**
     * `path` and `type` are both `String` and adjacent in the constructor. Swapped, the index would
     * filter every entry out as "not a blob" and the tab would come up empty with nothing logged.
     */
    @Test
    fun `tree entries differing only in type are not equal`() {
        val blob = ZefaniaRepositoryIndex.TreeEntry(path = "bibles/ENG/KJV.zip", type = "blob")

        assertEquals(blob, blob.copy())
        assertNotEquals(blob, blob.copy(type = "tree"))
        assertNotEquals(blob.hashCode(), blob.copy(path = "bibles/DEU/LUT.zip").hashCode())
    }

    @Test
    fun `an empty tree entry reads as blanks and zero`() {
        val blank = ZefaniaRepositoryIndex.TreeEntry()

        assertEquals("", blank.path)
        assertEquals("", blank.type)
        assertEquals("", blank.sha)
        assertEquals(0, blank.size)
    }

    @Test
    fun `a tree response carries its entries and whether the listing was cut short`() {
        val full = ZefaniaRepositoryIndex.TreeResponse(
            tree = listOf(ZefaniaRepositoryIndex.TreeEntry(path = "a.zip", type = "blob")),
            truncated = true,
        )

        assertEquals(1, full.tree.size)
        assertTrue(full.truncated, "a truncated listing must not be mistaken for a complete one")
        assertEquals(full, full.copy())
        assertNotEquals(full, full.copy(truncated = false))
    }

    @Test
    fun `an empty tree response is not truncated and lists nothing`() {
        val blank = ZefaniaRepositoryIndex.TreeResponse()

        assertTrue(blank.tree.isEmpty())
        assertEquals(false, blank.truncated)
    }
}
