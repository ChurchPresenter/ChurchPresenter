package org.churchpresenter.converter.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenameRulesTest {

    @Test
    fun `each letter case option reshapes the name, and an unknown one leaves it alone`() {
        assertEquals("Amazing grace", applyCase("amAZing GRACE", "Sentence case"))
        assertEquals("Amazing Grace OK", applyCase("amazing Grace OK", "Title Case"))
        assertEquals("amazing grace", applyCase("Amazing Grace", "lowercase"))
        assertEquals("AMAZING GRACE", applyCase("Amazing Grace", "UPPERCASE"))
        assertEquals("Amazing Grace", applyCase("Amazing Grace", "Something else"))
        assertEquals("Amazing Grace", applyCase("Amazing Grace", "None"))
    }

    @Test
    fun `the example follows every option`() {
        val before = Strings.renameExampleBefore
        assertEquals(before, renameExample(stripNumbers = false, renameToFirstVerse = false, caseOption = "None"))
        assertEquals(
            "${Strings.renameExampleFirstLine.lowercase()}.song",
            renameExample(stripNumbers = true, renameToFirstVerse = true, caseOption = "lowercase"),
        )
    }

    @Test
    fun `the first verse line is read after the frontmatter, from the primary half only`() = withTempDir("rename-rules") { dir ->
        fun song(name: String, body: String) = File(dir, name).apply { writeText(body) }

        assertEquals(
            "Amazing grace",
            extractFirstVerseLine(song("a.song", "---\nauthor: x\n---\n[Primary]\ntitle: T\n[Verse 1]\n\nAmazing grace\n")),
        )
        assertNull(extractFirstVerseLine(song("b.song", "---\n---\n[Primary]\n[Chorus]\nonly a chorus\n")))
        assertNull(extractFirstVerseLine(song("c.song", "---\n---\n[Primary]\n[Verse 1]\n[Chorus]\nchorus line\n")))
        assertNull(extractFirstVerseLine(song("d.song", "---\n---\n[Primary]\n[Secondary]\n[Verse 1]\nsecond half\n")))
        assertNull(extractFirstVerseLine(song("e.song", "---\n---\nnot primary\n[Verse 1]\nline\n")))
    }

    @Test
    fun `the preview keeps names that need no change and flags names already taken`() = withTempDir("rename-preview") { dir ->
        File(dir, "Grace.song").writeText("x")
        File(dir, "1 - Grace.song").writeText("x")
        File(dir, "notes.txt").writeText("x")
        val preview = buildRenamePreview(dir, stripNumbers = true, renameToFirstVerse = false)
        assertEquals(listOf("Grace.song", "Grace.song"), preview.map { it.newName })
        assertTrue(preview.single { it.file.name == "1 - Grace.song" }.conflict)
        assertTrue(!preview.single { it.file.name == "Grace.song" }.conflict)
    }
}
