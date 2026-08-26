package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which files the Bible picker offers, and what each one is called.
 *
 * The folder an operator points this at is a real folder on a real machine — a USB stick, a synced
 * drive, a path typed by hand — so the listing has to cope with what turns up there without
 * throwing.
 *
 * These cases were written against `BibleSettingsViewModel.filesInDirectory`/`fileDisplayNames`.
 * That class is gone: the settings tab now reads the folder through [readBibleFolderListing] on
 * `Dispatchers.IO` instead of in composition, and the view model held nothing else the tab used.
 * The selection and refresh-counter cases went with it — that state no longer exists.
 * [BibleTranslationNamesTest] covers the naming rules in more depth.
 */
class BibleFolderListingTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-listing-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun file(name: String) = File(dir, name).also { it.writeText("##Title: $name") }

    private fun files(directory: String = dir.path) = readBibleFolderListing(directory).files

    private fun names(directory: String = dir.path) = readBibleFolderListing(directory).displayNames

    // ── What counts as a module ─────────────────────────────────────────────────

    @Test
    fun `only files with the module extension are offered`() {
        file("kjv.spb")
        file("songs.sps")
        file("notes.txt")
        file("README")
        assertEquals(listOf("kjv.spb"), files())
    }

    @Test
    fun `a name that merely contains the extension is not a module`() {
        file("spb.txt")
        file("my.spb.backup")
        assertTrue(files().isEmpty(), "only the final extension counts")
    }

    @Test
    fun `modules are listed in name order`() {
        file("synodal.spb"); file("kjv.spb"); file("asv.spb")
        assertEquals(listOf("asv.spb", "kjv.spb", "synodal.spb"), files())
    }

    /**
     * Documents CURRENT behaviour: the sort is a plain lexicographic one, so capitalised names sort
     * ahead of lowercase ones rather than interleaving alphabetically ("Synodal" before "asv").
     * Cosmetic, and only visible in a folder with mixed capitalisation — the Songs picker sorts the
     * same way. Left as-is; a case-insensitive sort would change both.
     */
    @Test
    fun `capitalised names sort ahead of lowercase ones -- known quirk`() {
        file("asv.spb"); file("Synodal.spb"); file("KJV.spb")
        assertEquals(listOf("KJV.spb", "Synodal.spb", "asv.spb"), files())
    }

    @Test
    fun `a non-ascii module name is listed`() {
        file("Синодальный.spb")
        assertEquals(listOf("Синодальный.spb"), files())
    }

    @Test
    fun `an empty folder offers nothing`() {
        assertTrue(files().isEmpty())
    }

    /**
     * A *folder* named `x.spb` is not a module. It used to be offered as one — a dead row that
     * loaded an empty Bible — and this is reachable in practice, because some distributions ship a
     * folder per translation.
     */
    @Test
    fun `a folder named like a module is not offered`() {
        File(dir, "bundle.spb").mkdirs()
        file("kjv.spb")
        assertEquals(listOf("kjv.spb"), files())
    }

    // ── Subfolders ──────────────────────────────────────────────────────────────

    /**
     * Bible collections ship as nested folders — a directory per language, often one per
     * translation under that — so a listing confined to the top level finds almost nothing in a
     * real collection.
     */
    @Test
    fun `modules inside subfolders are offered, as paths relative to the folder`() {
        File(dir, "ENG/King James").mkdirs()
        File(dir, "ENG/King James/kjv.spb").writeText("##Title: KJV")
        File(dir, "RUS").mkdirs()
        File(dir, "RUS/synodal.spb").writeText("##Title: Synodal")
        file("root.spb")
        assertEquals(
            listOf("ENG/King James/kjv.spb", "RUS/synodal.spb", "root.spb"),
            files(),
        )
    }

    @Test
    fun `a nested module resolves back to its file`() {
        // Everything downstream resolves the stored value with File(storageDirectory, value), so
        // the separators in a relative path have to survive that round trip.
        File(dir, "ENG/Deep/Nested").mkdirs()
        File(dir, "ENG/Deep/Nested/kjv.spb").writeText("##Title: KJV")
        val listed = files().single()
        assertTrue(File(dir, listed).isFile, "'$listed' must resolve against the storage directory")
    }

    @Test
    fun `two modules of the same name in different folders are both offered`() {
        // The reason the listing carries paths rather than bare names: a collection can easily hold
        // two files called the same thing, and bare names would collapse them to one row.
        File(dir, "A").mkdirs(); File(dir, "B").mkdirs()
        File(dir, "A/kjv.spb").writeText("##Title: KJV A")
        File(dir, "B/kjv.spb").writeText("##Title: KJV B")
        assertEquals(listOf("A/kjv.spb", "B/kjv.spb"), files())
    }

    /**
     * Documents a KNOWN GAP: macOS writes `._name` resource-fork stubs beside real files on FAT
     * volumes (USB sticks are the common case here), and those carry the same extension, so they
     * appear in the picker as duplicate-looking dead rows.
     */
    @Test
    fun `mac resource-fork stubs are offered as modules -- known gap`() {
        file("kjv.spb")
        File(dir, "._kjv.spb").writeText(" ")
        assertEquals(listOf("._kjv.spb", "kjv.spb"), files())
    }

    // ── Bad folders ─────────────────────────────────────────────────────────────

    @Test
    fun `a folder that is not there offers nothing`() {
        assertTrue(files(File(dir, "not-created").path).isEmpty(), "a moved or unplugged drive must not throw")
    }

    @Test
    fun `a path pointing at a file offers nothing`() {
        val notAFolder = file("kjv.spb")
        assertTrue(files(notAFolder.path).isEmpty())
    }

    @Test
    fun `no folder at all offers nothing`() {
        file("kjv.spb")
        assertEquals(BibleFolderListing.EMPTY, readBibleFolderListing(""))
    }

    @Test
    fun `a folder that becomes unreachable stops offering its old contents`() {
        file("kjv.spb")
        assertEquals(1, files().size)

        dir.deleteRecursively() // the drive goes away mid-session

        assertTrue(files().isEmpty(), "the listing is read live, never cached")
    }

    // ── Names ───────────────────────────────────────────────────────────────────

    @Test
    fun `an unreadable module still gets a display-name entry`() {
        // The title is read from inside the file; a corrupt file must not throw and must not drop
        // the row out of the picker.
        File(dir, "broken.spb").writeText("not a bible")
        assertEquals(setOf("broken.spb"), names().keys)
    }

    @Test
    fun `two modules sharing a title are told apart by their folder`() {
        // The picker reverse-maps the chosen display name back to a file, so duplicate display names
        // would make one of the two unselectable. Collections nest a folder per translation and
        // routinely carry several editions with the same ##Title:.
        File(dir, "ENG").mkdirs(); File(dir, "RUS").mkdirs()
        File(dir, "ENG/a.spb").writeText("##Title:Holy Bible")
        File(dir, "RUS/b.spb").writeText("##Title:Holy Bible")
        File(dir, "unique.spb").writeText("##Title:Only One")

        val names = names()

        assertEquals(names.values.toSet().size, names.size, "display names must stay unique: $names")
        assertEquals("Only One", names["unique.spb"], "an unambiguous title is left alone")
        assertTrue(names.getValue("ENG/a.spb").contains("ENG"))
        assertTrue(names.getValue("RUS/b.spb").contains("RUS"))
    }

    @Test
    fun `a module not in the listing is named by its file name`() {
        file("kjv.spb")
        assertEquals("gone.spb", readBibleFolderListing(dir.path).nameOf("gone.spb"))
    }

    // ── Switching folders ───────────────────────────────────────────────────────

    @Test
    fun `the listing follows the folder`() {
        file("kjv.spb")
        val other = Files.createTempDirectory("cp-bible-listing-other").toFile()
        try {
            File(other, "synodal.spb").writeText("##Title: Синодальный перевод")

            assertEquals(listOf("synodal.spb"), files(other.path), "the previous folder's modules must not linger")
        } finally {
            other.deleteRecursively()
        }
    }

    @Test
    fun `a same-named module in another folder is read from the new folder`() {
        file("bible.spb") // titled "bible.spb" by the helper
        val other = Files.createTempDirectory("cp-bible-listing-other").toFile()
        try {
            File(other, "bible.spb").writeText("##Title: A Different Bible")

            assertEquals(
                "A Different Bible",
                readBibleFolderListing(other.path).nameOf("bible.spb"),
                "names must resolve against the folder in force now, not the one they were listed from"
            )
        } finally {
            other.deleteRecursively()
        }
    }

    // ── Renames ─────────────────────────────────────────────────────────────────

    @Test
    fun `the scan keeps each module's own title so a rename costs no disk`() {
        file("kjv.spb")

        assertEquals(mapOf("kjv.spb" to "kjv.spb"), readBibleFolderListing(dir.path).titles)
    }

    @Test
    fun `a rename renames only its own module`() {
        file("kjv.spb")
        file("rst.spb")

        val listing = readBibleFolderListing(dir.path)

        assertEquals(
            mapOf("kjv.spb" to "Authorised", "rst.spb" to "rst.spb"),
            listing.namesWith(mapOf("kjv.spb" to "Authorised")),
        )
    }

    @Test
    fun `no renames leaves the scanned names exactly as they were`() {
        file("kjv.spb")
        val listing = readBibleFolderListing(dir.path)

        assertEquals(listing.displayNames, listing.namesWith(emptyMap()))
    }

    @Test
    fun `renaming does not change what the listing was scanned as`() {
        // The picker asks for renamed names; `displayNames` stays the modules' own, so a rename
        // typed and then cleared cannot leave the scan holding a stale name.
        file("kjv.spb")
        val listing = readBibleFolderListing(dir.path)

        listing.namesWith(mapOf("kjv.spb" to "Authorised"))

        assertEquals("kjv.spb", listing.nameOf("kjv.spb"))
    }
}
