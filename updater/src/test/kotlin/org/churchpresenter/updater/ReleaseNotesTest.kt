package org.churchpresenter.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reading a release's notes for the update window: the groups and bullets the releases are written
 * in, the pull requests each line names pulled out to be drawn as links, and the markup taken off
 * the words — the window must never show a raw `**`, `(#123)` or `---`.
 */
class ReleaseNotesTest {

    @Test
    fun `bold lines start groups and dashes are their bullets, in the order written`() {
        val groups = parseReleaseNotes(
            """
            **Songs**
            - Song compare (#667)
            - Secondary song color (#556)

            **Fixes**
            - Keyboard focus is restored after closing a dialog (#686)
            """.trimIndent(),
        )
        assertEquals(listOf("Songs", "Fixes"), groups.map { it.title })
        assertEquals(
            listOf(
                NotesLine("Song compare", listOf(667), bullet = true),
                NotesLine("Secondary song color", listOf(556), bullet = true),
            ),
            groups[0].lines,
        )
        assertEquals(listOf(686), groups[1].lines.single().pulls)
    }

    @Test
    fun `markdown headings start groups too, with or without closing hashes`() {
        val groups = parseReleaseNotes("## What's Changed\n- One\n### Fixes ###\n- Two")
        assertEquals(listOf("What's Changed", "Fixes"), groups.map { it.title })
    }

    @Test
    fun `a bold title with a trailing colon is still a group`() {
        assertEquals("Media", parseReleaseNotes("**Media:**\n- Video walls (#721)").single().title)
        assertEquals("Media", parseReleaseNotes("**Media**:\n- Video walls (#721)").single().title)
    }

    @Test
    fun `every pull request a line names comes out, and the commas around them close up`() {
        val line = parseReleaseNotes("- Lottie text shaping (#732), caption reading and song go-lives (#733)")
            .single().lines.single()
        assertEquals(listOf(732, 733), line.pulls)
        assertEquals("Lottie text shaping, caption reading and song go-lives", line.text)
    }

    @Test
    fun `GitHub's generated lines give up their author and pull request link`() {
        val line = parseReleaseNotes(
            "* Add calendar sync by @someone in https://github.com/ChurchPresenter/ChurchPresenter/pull/812",
        ).single().lines.single()
        assertEquals(NotesLine("Add calendar sync", listOf(812), bullet = true), line)
    }

    @Test
    fun `inline markup is taken off the words`() {
        val line = parseReleaseNotes("- **Bold** move of `:songs` to [the module](https://example.invalid)")
            .single().lines.single()
        assertEquals("Bold move of :songs to the module", line.text)
    }

    @Test
    fun `a paragraph before the first group belongs to no group`() {
        val groups = parseReleaseNotes("Nightly build of `ff56d71c4`.\n\n**Songs**\n- Keep the search (#816)")
        assertEquals(null, groups.first().title)
        assertEquals(
            NotesLine("Nightly build of ff56d71c4.", emptyList(), bullet = false),
            groups.first().lines.single(),
        )
        assertEquals("Songs", groups[1].title)
    }

    @Test
    fun `a horizontal rule ends the notes, so the per-platform download list never shows`() {
        val groups = parseReleaseNotes(
            """
            **UI / General**
            - Calendar manager (#565)

            ---
            Download the installer for your platform below.

            - **Windows:** ChurchPresenter-26.12.171-WINDOWS-x64.msi
            """.trimIndent(),
        )
        assertEquals(listOf("Calendar manager"), groups.single().lines.map { it.text })
    }

    @Test
    fun `blank notes give no groups at all`() {
        assertTrue(parseReleaseNotes("").isEmpty())
        assertTrue(parseReleaseNotes("\n   \n").isEmpty())
    }

    @Test
    fun `a pull request's number leads to its page on GitHub`() {
        assertEquals("https://github.com/ChurchPresenter/ChurchPresenter/pull/693", pullRequestUrl(693))
    }
}
