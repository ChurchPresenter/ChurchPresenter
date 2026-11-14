@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.converter.library.SongAliases
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongAliasTabTest {

    private val body = "---\nauthor: Someone\n---\n\n[Primary]\ntitle: T\n\nline\n"

    private fun song(dir: File, name: String, alias: String = ""): File = File(dir, name).apply {
        parentFile.mkdirs()
        writeText(if (alias.isEmpty()) body else SongAliases.withAlias(body, alias))
    }

    private fun aliasOf(file: File) = SongAliases.aliasIn(file.readText())

    private fun ComposeUiTest.typePattern(text: String) {
        onNode(hasSetTextAction()).performTextReplacement(text)
        waitForIdle()
    }

    @Test
    fun `a pattern sets the alias on every numbered song and leaves the rest alone`() = withTempDir("alias-set") { dir ->
        val one = song(dir, "0001 - One.song")
        val two = song(dir, "book/0002 - Two.song")
        val loose = song(dir, "Loose.song")
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { SongAliasTab() }

            click(Strings.selectFolder)
            assertTrue(isShowing(dir.absolutePath))
            awaitShowing(Strings.aliasSummary(0, 2, 1))

            typePattern("Ch {n}")
            assertTrue(isShowing(Strings.aliasSummary(2, 0, 1)))
            assertTrue(isShowing("0001 - One.song"))
            assertTrue(isShowing("Ch 2"))

            click(Strings.aliasApplyN(2))
            awaitShowing(Strings.aliasDone(2, 0))
            awaitShowing(Strings.aliasSummary(0, 2, 1))
        }
        assertEquals("Ch 1", aliasOf(one))
        assertEquals("Ch 2", aliasOf(two))
        assertEquals(body, loose.readText(), "a song with no number is not touched")
    }

    @Test
    fun `a blank pattern removes the aliases already set`() = withTempDir("alias-clear") { dir ->
        val one = song(dir, "0001 - One.song", alias = "Ch 1")
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { SongAliasTab() }

            click(Strings.selectFolder)
            awaitShowing(Strings.aliasSummary(1, 0, 0))
            assertTrue(isShowing("Ch 1"))
            assertTrue(isShowing(Strings.aliasNone))

            click(Strings.aliasClearN(1))
            awaitShowing(Strings.aliasDone(1, 0))
        }
        assertEquals(body, one.readText())
    }

    @Test
    fun `a song that cannot be written is counted as failed`() = withTempDir("alias-fail") { dir ->
        val locked = song(dir, "0003 - Locked.song").apply { setWritable(false) }
        try {
            runComposeUiTest {
                setConverterContent(FakePickers(directory = dir)) { SongAliasTab() }

                click(Strings.selectFolder)
                awaitShowing(Strings.aliasSummary(0, 1, 0))
                typePattern("A")
                click(Strings.aliasApplyN(1))
                awaitShowing(Strings.aliasDone(0, 1))
            }
            assertEquals(body, locked.readText())
        } finally {
            locked.setWritable(true)
        }
    }

    @Test
    fun `nothing can be applied before a folder is chosen`() = runComposeUiTest {
        setConverterContent(FakePickers(directory = null)) { SongAliasTab() }

        click(Strings.selectFolder)
        typePattern("Ch")
        assertTrue(isShowing(Strings.aliasApplyN(0)))
        assertTrue(isShowing(Strings.aliasPatternHelp))
    }
}
