package org.churchpresenter.helper.report

import org.churchpresenter.helper.intent.ResolveContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactTest {

    private val context = ResolveContext(language = "en")

    private fun you(text: String) = ChatLine(fromOperator = true, text = text)
    private fun wick(text: String) = ChatLine(fromOperator = false, text = text)

    private fun redact(vararg lines: ChatLine, names: ChatNames = ChatNames()) =
        redactChat(lines.toList(), names, context).map { it.text }

    @Test
    fun `a page to parents is hidden, and so is Wick repeating it`() {
        val (asked, confirm) = redact(
            you("tell the parents of Sam to come to the nursery"),
            wick("Put this on screen as an announcement: “Parents of Sam, please come to the nursery.”?"),
        )
        assertEquals(MASK_ANNOUNCEMENT, asked)
        assertFalse("Sam" in confirm, confirm)
        assertTrue(MASK_ANNOUNCEMENT in confirm, confirm)
    }

    @Test
    fun `an announcement is hidden`() {
        val (asked, confirm) = redact(
            you("announce: Coffee after the service"),
            wick("Put this on screen as an announcement: “Coffee after the service”?"),
        )
        assertEquals(MASK_ANNOUNCEMENT, asked)
        assertEquals("Put this on screen as an announcement: “$MASK_ANNOUNCEMENT”?", confirm)
    }

    @Test
    fun `quoted text is hidden, but a mask already in quotes is left`() {
        val (added, kept) = redact(wick("Added “Amazing Grace” and \"How Great\""), wick("Shown “[text]”"))
        assertEquals("Added “$MASK_TEXT” and \"$MASK_TEXT\"", added)
        assertEquals("Shown “[text]”", kept)
    }

    @Test
    fun `the church's own profile and output names are hidden as whole words`() {
        val names = ChatNames(profiles = listOf("Livestream", "A"), outputs = listOf("Lobby TV"))
        val (line) = redact(you("make the livestream and Lobby TV blue, not Livestreamer or a"), names = names)
        assertEquals("make the $MASK_PROFILE and $MASK_OUTPUT blue, not Livestreamer or a", line)
    }

    @Test
    fun `long numbers are hidden and Bible references stay`() {
        val references = "John 3:16, Psalm 119:105, Psalm 119, ps 150, verse 12"
        val (line) = redact(you("add song 12345 and call 5551234, then $references"))
        assertEquals(
            "add song $MASK_NUMBER and call $MASK_NUMBER, then $references",
            line,
        )
    }

    @Test
    fun `a request that is not an announcement is kept as typed`() {
        assertEquals(listOf("show John 3:16"), redact(you("show John 3:16")))
    }

    @Test
    fun `the transcript is one line per turn, the newest kept when it is too long`() {
        val lines = listOf(you("one\ntwo"), wick("three"), you("four"))
        assertEquals("You: one two\nWick: three\nYou: four", transcriptOf(lines))
        assertEquals("Wick: three\nYou: four", transcriptOf(lines, maxChars = 22))
        assertEquals("", transcriptOf(lines, maxChars = 3))
    }

    @Test
    fun `a note goes first, trimmed and capped, and a blank one is left out`() {
        assertEquals("You: hi", chatMessage("  ", "You: hi"))
        assertEquals("Note: it froze\n\nYou: hi", chatMessage(" it froze ", "You: hi"))
        val long = chatMessage("x".repeat(MAX_NOTE_CHARS + 50), "")
        assertEquals("Note: ".length + MAX_NOTE_CHARS + 2, long.length)
    }
}
