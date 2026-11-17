package org.churchpresenter.bibleeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordDiffTest {

    private fun render(spans: List<DiffSpan>) = spans.joinToString("") {
        when (it.kind) {
            DiffKind.SAME -> it.text
            DiffKind.DELETED -> "[-${it.text}]"
            DiffKind.INSERTED -> "[+${it.text}]"
        }
    }

    @Test
    fun `a changed word is shown struck out and replaced`() {
        assertEquals("his face did [-shnie][+shine] as the sun", render(wordDiff("his face did shnie as the sun",
            "his face did shine as the sun")))
    }

    @Test
    fun `text typed into an empty verse is all inserted`() {
        assertEquals(listOf(DiffSpan("Howbeit this kind.", DiffKind.INSERTED)), wordDiff("", "Howbeit this kind."))
    }

    @Test
    fun `text taken away is all deleted, trailing words included`() {
        assertEquals("one[- two three]", render(wordDiff("one two three", "one")))
    }

    @Test
    fun `no change is one unchanged run`() {
        assertEquals(listOf(DiffSpan("same words", DiffKind.SAME)), wordDiff("same words", "same words"))
    }

    @Test
    fun `long unchanged runs are shortened so the change shows`() {
        val words = (1..20).joinToString(" ") { "w$it" }
        val start = render(wordDiff("$words old", "$words new"))
        assertEquals("… w17 w18 w19 w20 [-old][+new]", start)
        val end = render(wordDiff("old $words", "new $words"))
        assertTrue(end.startsWith("[-old][+new] w1 w2 w3") && end.endsWith(" …"), end)
        val middle = render(wordDiff("a $words b", "x $words y"))
        assertTrue(middle.contains(" … ") && !middle.contains("w10"), middle)
    }
}
